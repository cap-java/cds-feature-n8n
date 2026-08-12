using {test} from './db/schema';

service TestService {
  entity Items  as projection on test.Items;
  entity Orders as projection on test.Orders;
}

annotate TestService.Items with @n8n.process.start: [
  {on: 'CREATE', path: 'item-created', inputs: [$self.ID, $self.title]},
  {on: 'DELETE', path: 'item-deleted', inputs: [$self.ID, $self.title]}
];

annotate TestService.Orders with @n8n.process.start: [
  {on: 'CREATE', path: 'order-created'}
];
