using {sap.capire.bookshop as my} from '../db/schema';

service CatalogService {

  /** For displaying lists of Books */
  @readonly
  entity ListOfBooks as
    projection on Books
    excluding {
      descr
    };

  /** For display in details pages */
  @readonly
  entity Books       as
    projection on my.Books {
      *,
      author.name as author
    }
    excluding {
      createdBy,
      modifiedBy
    };

  action submitOrder(book : Books:ID, quantity : Integer) returns {
    stock : Integer
  };

  action confirmOrder(book: Books:ID, quantity : Integer, buyer : String) returns {
    orderId : String
  };

  event OrderedBook : {
    book     : Books:ID;
    quantity : Integer;
    buyer    : String
  };
}

annotate CatalogService.submitOrder with @n8n.process.start: {on: 'submitOrder'};

