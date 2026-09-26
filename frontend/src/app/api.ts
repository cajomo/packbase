import { HttpErrorResponse } from '@angular/common/http';

/** Wire types, kept in sync by hand with api/openapi.yaml. */

export const API = '/api/v1';

export interface UserProfileDto {
  id: string;
  email: string;
}

export interface GearItemDto {
  id: string;
  name: string;
  category?: string | null;
  weightGrams?: number | null;
  quantity?: number | null;
  notes?: string | null;
  createdAt: string;
}

export interface GearItemInputDto {
  name: string;
  category: string;
  weightGrams?: number;
  quantity?: number;
  notes?: string;
}

export interface ListEntryDto {
  itemId: string;
  quantity: number;
  /** Omitted/null = use the item's category, '' = uncategorized. */
  category?: string | null;
}

export interface PackListDto {
  id: string;
  name: string;
  createdAt: string;
  entries: ListEntryDto[];
}

export interface PackListInputDto {
  name: string;
  entries?: ListEntryDto[];
}

/** A user-facing message for a failed request; prefers the server's own message on 400s. */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  if (error.status === 0) return "Can't reach the server. Check your connection and try again.";
  if (error.status === 403) return 'The request was rejected. Reload the page and try again.';
  if (error.status === 400 && typeof error.error?.message === 'string') return error.error.message;
  return fallback;
}
