/** Shared API contract types. See docs/architecture/api-overview.md */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiError {
  timestamp?: string;
  status: number;
  errorCode: string;
  message: string;
  path?: string;
  correlationId?: string;
  fieldErrors?: FieldError[];
}

export interface ImageRef {
  imageId: number;
  url: string;
  thumbUrl: string;
  alt: string;
  sortOrder: number;
  primary: boolean;
}

export interface AccountMe {
  userId: number;
  fullName: string;
  username: string;
  email: string;
  mobileNumber: string | null;
  emailVerified: boolean;
  roles: string[];
}
