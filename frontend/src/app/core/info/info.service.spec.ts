import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { InfoService } from './info.service';

describe('InfoService', () => {
  let svc: InfoService;
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    svc = TestBed.inject(InfoService);
  });
  afterEach(() => vi.useRealTimers());

  it('shows a popup with the default title for its kind', () => {
    svc.error('Name is required.');
    expect(svc.messages()).toHaveLength(1);
    expect(svc.messages()[0]).toMatchObject({ kind: 'error', title: 'Oh no!', text: 'Name is required.' });
  });

  it('replaces an identical message instead of stacking copies', () => {
    svc.success('Saved.');
    svc.success('Saved.');
    expect(svc.messages()).toHaveLength(1);
  });

  it('ignores blank text and keeps at most four popups', () => {
    svc.error('   ');
    expect(svc.messages()).toHaveLength(0);
    for (let i = 0; i < 6; i++) svc.info('m' + i);
    expect(svc.messages()).toHaveLength(4);
    expect(svc.messages()[3].text).toBe('m5');
  });

  it('dismisses automatically after its ttl', () => {
    svc.success('Saved.');
    vi.advanceTimersByTime(4100);
    expect(svc.messages()).toHaveLength(0);
  });
});
