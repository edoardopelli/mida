import { TestBed } from '@angular/core/testing';
import { urlBase64ToUint8Array } from './push.service';

describe('PushService helpers', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('converte una chiave VAPID base64url in byte', () => {
    const key = 'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U';
    const bytes = urlBase64ToUint8Array(key);

    expect(bytes).toBeInstanceOf(Uint8Array);
    expect(bytes).toHaveLength(65);
    expect(bytes[0]).toBe(4);
    expect(bytes[1]).toBe(0x49);
  });

  it('gestisce il padding e i caratteri base64url', () => {
    expect(urlBase64ToUint8Array('AQID')).toEqual(new Uint8Array([1, 2, 3]));
    expect(urlBase64ToUint8Array('-_8')).toEqual(new Uint8Array([0xfb, 0xff]));
  });
});
