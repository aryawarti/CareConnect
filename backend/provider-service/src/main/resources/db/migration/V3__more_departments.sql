-- A full hospital department catalogue. V1 seeded five for the original clinic
-- scope; as CareConnect grows into a hospital system (SRS ch. 13 roadmap) the
-- directory needs the real breadth so a doctor signing up can pick the right
-- home. ON CONFLICT keeps this idempotent and non-duplicating with V1.
INSERT INTO departments (name) VALUES
    ('Emergency Medicine'),
    ('General Surgery'),
    ('Internal Medicine'),
    ('Cardiothoracic Surgery'),
    ('Neurology'),
    ('Neurosurgery'),
    ('Obstetrics & Gynecology'),
    ('Neonatology'),
    ('Ophthalmology'),
    ('ENT (Otorhinolaryngology)'),
    ('Dentistry & Oral Surgery'),
    ('Psychiatry'),
    ('Pulmonology'),
    ('Gastroenterology'),
    ('Nephrology'),
    ('Urology'),
    ('Endocrinology'),
    ('Oncology'),
    ('Hematology'),
    ('Rheumatology'),
    ('Radiology & Imaging'),
    ('Pathology & Laboratory Medicine'),
    ('Anesthesiology'),
    ('Physiotherapy & Rehabilitation'),
    ('Nutrition & Dietetics'),
    ('Plastic & Reconstructive Surgery'),
    ('Vascular Surgery'),
    ('Infectious Diseases'),
    ('Immunology & Allergy'),
    ('Pain Management'),
    ('Palliative Care'),
    ('Geriatrics'),
    ('Family Medicine')
ON CONFLICT (name) DO NOTHING;
