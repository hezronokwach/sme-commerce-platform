import { shopAddress, whatsAppShareLink } from './shop-link';

describe('shop link', () => {
  it("builds the shop's storefront address from its web name", () => {
    expect(shopAddress('https://shops.example', 'kamau-hardware')).toBe('https://shops.example/s/kamau-hardware');
  });

  it('fills a WhatsApp message with the name and the address', () => {
    const link = whatsAppShareLink('Kamau Hardware', 'https://shops.example/s/kamau-hardware');

    expect(link).toBe(
      'https://wa.me/?text=' + encodeURIComponent('Shop at Kamau Hardware: https://shops.example/s/kamau-hardware'),
    );
  });
});
