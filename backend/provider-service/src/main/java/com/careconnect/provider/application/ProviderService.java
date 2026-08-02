package com.careconnect.provider.application;

import com.careconnect.provider.api.dto.DoctorDtos.CreateDoctorRequest;
import com.careconnect.provider.api.dto.DoctorDtos.ExceptionRequest;
import com.careconnect.provider.api.dto.DoctorDtos.ReplaceAvailabilityRequest;
import com.careconnect.provider.api.dto.DoctorDtos.UpdateDoctorRequest;
import com.careconnect.provider.domain.AvailabilitySlot;
import com.careconnect.provider.domain.Department;
import com.careconnect.provider.domain.Doctor;
import com.careconnect.provider.domain.DoctorNotFoundException;
import com.careconnect.provider.domain.DoctorStatus;
import com.careconnect.provider.domain.OverlappingSlotException;
import com.careconnect.provider.domain.ScheduleException;
import com.careconnect.provider.infrastructure.repository.AvailabilitySlotRepository;
import com.careconnect.provider.infrastructure.repository.DepartmentRepository;
import com.careconnect.provider.infrastructure.repository.DoctorRepository;
import com.careconnect.provider.infrastructure.repository.ScheduleExceptionRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    private final DoctorRepository doctors;
    private final DepartmentRepository departments;
    private final AvailabilitySlotRepository slots;
    private final ScheduleExceptionRepository exceptions;

    public ProviderService(DoctorRepository doctors, DepartmentRepository departments,
                           AvailabilitySlotRepository slots, ScheduleExceptionRepository exceptions) {
        this.doctors = doctors;
        this.departments = departments;
        this.slots = slots;
        this.exceptions = exceptions;
    }

    // ---- departments -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Department> departments() {
        return departments.findAll();
    }

    /**
     * Departments with how many bookable doctors each has.
     *
     * The count is the point: a patient browsing departments should not be able
     * to walk into an empty one and find out only after the click. Two queries
     * total (departments, then one grouped count) rather than a count per
     * department.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> doctorCountsByDepartment() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : doctors.countActiveByDepartment()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    // ---- doctors -----------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Doctor> directory(String query, UUID departmentId, Pageable pageable) {
        return doctors.directory(query, DoctorStatus.ACTIVE, departmentId, pageable);
    }

    /**
     * Which weekdays each of these doctors actually works.
     *
     * Fetched in one query for the whole page rather than per doctor: the
     * directory renders up to 20 cards and "Mon, Wed, Fri" on each of them must
     * not cost 20 round trips.
     *
     * An empty set is meaningful, not missing data — it means nobody has set
     * this doctor's schedule yet, so they cannot be booked. Callers show that
     * plainly instead of letting a patient reach an empty calendar.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Set<Integer>> workingDaysFor(List<UUID> doctorIds) {
        Map<UUID, Set<Integer>> byDoctor = new HashMap<>();
        if (doctorIds.isEmpty()) {
            return byDoctor;
        }
        for (UUID id : doctorIds) {
            byDoctor.put(id, new TreeSet<>());
        }
        for (AvailabilitySlot slot : slots.findByDoctorIdIn(doctorIds)) {
            byDoctor.computeIfAbsent(slot.getDoctorId(), k -> new TreeSet<>())
                    .add(slot.getDayOfWeek());
        }
        return byDoctor;
    }

    /**
     * Everything a patient needs to decide whether to book this doctor, in one
     * call: credentials, fee, the weekly pattern, and the days they are away.
     *
     * Assembled server-side rather than left to the client to stitch from three
     * endpoints — the profile page would otherwise show its four sections
     * arriving one at a time.
     */
    @Transactional(readOnly = true)
    public DoctorProfileView profile(UUID doctorId) {
        Doctor doctor = get(doctorId);
        return new DoctorProfileView(
                doctor,
                slots.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId),
                exceptions.findByDoctorIdAndExceptionDateGreaterThanEqualOrderByExceptionDate(
                        doctorId, LocalDate.now()));
    }

    /** Assembled view of a doctor; mapped to a DTO at the API boundary. */
    public record DoctorProfileView(Doctor doctor, List<AvailabilitySlot> weekly,
                                    List<ScheduleException> timeOff) {
    }

    @Transactional
    public Doctor create(CreateDoctorRequest request) {
        Department department = departments.findById(request.departmentId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown department"));
        Doctor doctor = new Doctor(request.firstName(), request.lastName(),
                request.specialty(), department, request.consultationFee());
        doctor.setEmail(request.email());
        doctor.setPhone(request.phone());
        if (request.userId() != null) {
            doctor.linkUser(request.userId());
        }
        doctors.save(doctor);
        log.info("doctor created id={}", doctor.getId());
        return doctor;
    }

    /**
     * A doctor applying to join, using their own account. Mirrors patient
     * self-onboarding, with one crucial difference: the profile is created
     * PENDING and stays invisible to patients until an administrator verifies
     * the qualification and medical registration number.
     */
    @Transactional
    public Doctor apply(UUID userId, com.careconnect.provider.api.dto.DoctorDtos.DoctorApplicationRequest request,
                        String email) {
        if (doctors.findByUserId(userId).isPresent()) {
            throw new IllegalArgumentException("You already have a doctor profile");
        }
        Department department = departments.findById(request.departmentId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown department"));
        Doctor doctor = new Doctor(request.firstName(), request.lastName(), request.specialty(),
                department, request.consultationFee() == null
                        ? java.math.BigDecimal.valueOf(500) : request.consultationFee());
        doctor.linkUser(userId);
        doctor.setEmail(email);
        doctor.setPhone(request.phone());
        doctor.updateCredentials(request.qualification(), request.registrationNo(),
                request.experienceYears() == null ? null : request.experienceYears().shortValue(),
                request.bio());
        doctor.submitForVerification();
        doctors.save(doctor);
        log.info("doctor application submitted id={} reg={}", doctor.getId(), request.registrationNo());
        return doctor;
    }

    /** Applications an administrator still has to review. */
    @Transactional(readOnly = true)
    public List<Doctor> pendingApplications() {
        return doctors.findByVerificationOrderByIdAsc(
                com.careconnect.provider.domain.VerificationStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Page<Doctor> all(Pageable pageable) {
        return doctors.findAllByOrderByLastNameAsc(pageable);
    }

    @Transactional
    public Doctor approve(UUID id) {
        Doctor doctor = get(id);
        doctor.approve();
        log.info("doctor {} approved — now visible to patients", id);
        return doctor;
    }

    @Transactional
    public Doctor reject(UUID id, String reason) {
        Doctor doctor = get(id);
        doctor.reject(reason);
        log.info("doctor {} rejected: {}", id, reason);
        return doctor;
    }

    @Transactional(readOnly = true)
    public Doctor get(UUID id) {
        return doctors.findById(id).orElseThrow(() -> new DoctorNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Doctor getOwn(UUID userId) {
        return doctors.findByUserId(userId)
                .orElseThrow(() -> new DoctorNotFoundException(
                        "No doctor profile linked to your account"));
    }

    @Transactional
    public Doctor update(UUID id, UpdateDoctorRequest request) {
        Doctor doctor = get(id);
        if (request.firstName() != null) doctor.setFirstName(request.firstName());
        if (request.lastName() != null) doctor.setLastName(request.lastName());
        if (request.specialty() != null) doctor.setSpecialty(request.specialty());
        if (request.departmentId() != null) {
            doctor.setDepartment(departments.findById(request.departmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown department")));
        }
        if (request.consultationFee() != null) doctor.setConsultationFee(request.consultationFee());
        doctor.setEmail(request.email());
        doctor.setPhone(request.phone());
        return doctor;
    }

    @Transactional
    public void deactivate(UUID id) {
        get(id).deactivate();
        log.info("doctor deactivated id={}", id);
    }

    // ---- availability ------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AvailabilitySlot> availability(UUID doctorId) {
        get(doctorId);   // 404 before empty-list ambiguity
        return slots.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
    }

    /**
     * Replace-all semantics: the client sends the complete weekly schedule.
     * Idempotent, no slot-level diffing, and overlap validation sees the whole
     * picture at once. Caller must be STAFF/ADMIN or the owning doctor.
     */
    @Transactional
    public List<AvailabilitySlot> replaceAvailability(UUID doctorId, UUID callerUserId,
                                                      boolean isStaff,
                                                      ReplaceAvailabilityRequest request) {
        Doctor doctor = get(doctorId);
        requireStaffOrOwner(doctor, callerUserId, isStaff);

        List<AvailabilitySlot> proposed = request.slots().stream()
                .map(s -> {
                    if (!s.startTime().isBefore(s.endTime())) {
                        throw new OverlappingSlotException(
                                "start must be before end (%s–%s)".formatted(s.startTime(), s.endTime()));
                    }
                    return new AvailabilitySlot(doctorId, s.dayOfWeek(),
                            s.startTime(), s.endTime(), s.slotMinutes());
                })
                .toList();
        for (int i = 0; i < proposed.size(); i++) {
            for (int j = i + 1; j < proposed.size(); j++) {
                if (proposed.get(i).overlaps(proposed.get(j))) {
                    throw new OverlappingSlotException("Slots overlap on day "
                            + proposed.get(i).getDayOfWeek());
                }
            }
        }
        slots.deleteByDoctorId(doctorId);
        return slots.saveAll(proposed);
    }

    /** One-call booking view for appointment-service (see BookingInfo). */
    @Transactional(readOnly = true)
    public com.careconnect.provider.api.dto.BookingInfo bookingInfo(UUID doctorId, LocalDate date) {
        Doctor doctor = get(doctorId);
        boolean dayOff = !exceptions
                .findByDoctorIdAndExceptionDateGreaterThanEqualOrderByExceptionDate(doctorId, date)
                .stream().filter(e -> e.getExceptionDate().equals(date)).findAny().isEmpty();
        List<com.careconnect.provider.api.dto.BookingInfo.Window> windows = dayOff ? List.of()
                : slots.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream()
                        .filter(s -> s.getDayOfWeek() == date.getDayOfWeek().getValue())
                        .map(s -> new com.careconnect.provider.api.dto.BookingInfo.Window(
                                s.getStartTime(), s.getEndTime(), s.getSlotMinutes()))
                        .toList();
        return new com.careconnect.provider.api.dto.BookingInfo(doctor.getId(),
                doctor.getStatus() == DoctorStatus.ACTIVE,
                doctor.getFirstName() + " " + doctor.getLastName(),
                doctor.getConsultationFee(), dayOff, windows);
    }

    // ---- schedule exceptions ----------------------------------------------

    @Transactional(readOnly = true)
    public List<ScheduleException> upcomingExceptions(UUID doctorId) {
        get(doctorId);
        return exceptions.findByDoctorIdAndExceptionDateGreaterThanEqualOrderByExceptionDate(
                doctorId, LocalDate.now());
    }

    @Transactional
    public ScheduleException addException(UUID doctorId, UUID callerUserId,
                                          boolean isStaff, ExceptionRequest request) {
        Doctor doctor = get(doctorId);
        requireStaffOrOwner(doctor, callerUserId, isStaff);
        return exceptions.save(new ScheduleException(doctorId, request.date(), request.reason()));
    }

    @Transactional
    public void removeException(UUID doctorId, UUID callerUserId, boolean isStaff, UUID exceptionId) {
        Doctor doctor = get(doctorId);
        requireStaffOrOwner(doctor, callerUserId, isStaff);
        exceptions.deleteById(exceptionId);
    }

    /** Staff manage anyone; a doctor manages only their own schedule. */
    private void requireStaffOrOwner(Doctor doctor, UUID callerUserId, boolean isStaff) {
        if (!isStaff && !doctor.isOwnedBy(callerUserId)) {
            throw new AccessDeniedException("Not your schedule");
        }
    }
}
