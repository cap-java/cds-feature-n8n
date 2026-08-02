using {test} from './db/schema';

service TestService {
  entity Items as projection on test.Items;
}

annotate TestService.Items with @n8n.process.start: [
  {on: 'CREATE', path: 'item-created', inputs: [$self.ID, $self.title]},
  {on: 'DELETE', path: 'item-deleted', inputs: [$self.ID, $self.title]}
];
