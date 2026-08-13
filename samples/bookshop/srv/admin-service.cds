using {sap.capire.bookshop as my} from '../db/schema';

service AdminService @(requires: 'admin') {
  entity Books   as projection on my.Books;
  entity Authors as projection on my.Authors;

  action confirmBookCreation(book: Books:ID, stock : Integer) returns {
    bookId : String
  };

  action confirmBookDeletion(book: Books:ID, author: String) returns {
    bookId : String
  };
}

annotate AdminService.Books with @n8n.process.start: [
  {on: 'DELETE', path: 'book-deleted',   if: (stock = 0), inputs: [$self.ID, $self.title, $self.author_ID]},
  {on: 'UPDATE', path: 'book-updated',   inputs: [$self.ID, $self.title]},
  {on: 'UPDATE', path: 'book-low-stock', inputs: [$self.ID, $self.title, $self.stock],
                                          if: (stock < 10)}
];

