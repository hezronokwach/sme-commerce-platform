import { thumbnailOf } from './photo-upload';

describe('thumbnailOf', () => {
  it('asks Cloudinary for a small square copy', () => {
    const url = 'https://res.cloudinary.com/demo/image/upload/v1/sme-commerce/shop/hammer.jpg';

    expect(thumbnailOf(url, 96)).toBe(
      'https://res.cloudinary.com/demo/image/upload/c_fill,w_96,h_96,f_auto,q_auto/v1/sme-commerce/shop/hammer.jpg',
    );
  });

  it('leaves any other address as it is', () => {
    expect(thumbnailOf('https://example.com/image/upload/a.jpg', 96)).toBe('https://example.com/image/upload/a.jpg');
  });
});
