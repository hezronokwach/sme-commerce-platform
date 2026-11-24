import { titleFromFileName, toLists } from './assistant-page';

describe('toLists', () => {
  it("keeps the owner's uploads and only counts the product documents", () => {
    const lists = toLists([
      { id: 'd1', title: 'Delivery and returns', sourceType: 'UPLOAD', status: 'READY' },
      { id: 'd2', title: 'Hammer', sourceType: 'PRODUCT', status: 'READY' },
      { id: 'd3', title: 'Padlock', sourceType: 'PRODUCT', status: 'READY' },
    ]);

    expect(lists.uploads.length).toBe(1);
    expect(lists.uploads[0].title).toBe('Delivery and returns');
    expect(lists.uploads[0].statusText).toBe('Ready');
    expect(lists.productCount).toBe(2);
    expect(lists.stillWorking).toBe(false);
  });

  it('notices a document that is still being read', () => {
    const lists = toLists([{ id: 'd1', title: 'Hours', sourceType: 'UPLOAD', status: 'PROCESSING' }]);

    expect(lists.stillWorking).toBe(true);
    expect(lists.uploads[0].statusText).toBe('Reading…');
  });
});

describe('titleFromFileName', () => {
  it('turns a file name into a title', () => {
    expect(titleFromFileName('delivery-and-returns.md')).toBe('Delivery and returns');
    expect(titleFromFileName('opening_hours.txt')).toBe('Opening hours');
  });
});
