using {test} from './db/schema';

service TestService {
  entity Items      as projection on test.Items;
  entity Orders     as projection on test.Orders;
  entity Categories as projection on test.Categories;

  @odata.draft.enabled
  entity DraftBooks as projection on test.DraftBooks;
}

annotate TestService.Items with @n8n.process.start: [
  {on: 'CREATE', path: 'item-created', inputs: [$self.ID, $self.title]},
  {on: 'CREATE', path: 'item-shipped', if: (status = 'shipped'), inputs: [$self.ID, $self.status]},
  {on: 'DELETE', path: 'item-deleted', inputs: [$self.ID, $self.title, $self.category.name]},
  {on: 'DELETE', path: 'item-active-deleted', if: (status != 'draft'), inputs: [$self.ID, $self.status]}
];

annotate TestService.Orders with @n8n.process.start: [
  {on: 'CREATE', path: 'order-created'}
];

annotate TestService.DraftBooks with @n8n.process.start: [
  {on: 'CREATE', path: 'draft-book-created', inputs: [$self.ID, $self.title]}
];
