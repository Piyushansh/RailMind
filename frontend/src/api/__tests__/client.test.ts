import { describe, it, expect } from 'vitest';
import { AxiosError } from 'axios';
import { extractErrorMessage } from '@/api/client';

describe('extractErrorMessage', () => {
  it('surfaces the backend message field when present', () => {
    const error = new AxiosError('Request failed', '409', undefined, undefined, {
      status: 409,
      statusText: 'Conflict',
      headers: {},
      config: {} as never,
      data: { status: 409, error: 'CONFLICT', message: 'Selected seat is no longer available' },
    });

    expect(extractErrorMessage(error)).toBe('Selected seat is no longer available');
  });

  it('falls back to a network message when there is no response at all', () => {
    const error = new AxiosError('Network Error');
    expect(extractErrorMessage(error)).toBe('Could not reach the server. Check your connection.');
  });

  it('falls back to a generic message for a non-axios error', () => {
    expect(extractErrorMessage(new Error('boom'))).toBe('Something went wrong. Please try again.');
  });
});
