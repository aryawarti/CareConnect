import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { humanizeError, isSystemError } from './http-status';

/**
 * One place that gives every request an enterprise-grade failure story.
 *
 * Division of responsibility:
 *  - SYSTEM problems (network down, 5xx, service still warming up) get a global
 *    toast here, because they are not specific to any one screen and every
 *    screen would otherwise have to reinvent the same message.
 *  - BUSINESS/validation problems (4xx) are left for the calling screen to show
 *    inline, next to the field or action that caused them, where there is
 *    enough context to explain what to fix.
 *
 * Either way the error is re-thrown so component handlers still run (stop a
 * spinner, roll back optimistic state, etc.).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (isSystemError(err)) {
        snackBar.open(humanizeError(err), 'Dismiss', {
          duration: 6000,
          panelClass: 'cc-snack-error',
          horizontalPosition: 'right',
          verticalPosition: 'top'
        });
      }
      return throwError(() => err);
    })
  );
};
