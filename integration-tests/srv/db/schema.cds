namespace test;

entity Categories {
  key ID   : UUID;
  name     : String;
}

entity Items {
  key ID       : UUID;
  title        : String;
  status       : String;
  category     : Association to Categories;
}

entity Orders {
  key ID    : UUID;
  total     : Integer;
}

entity DraftBooks {
  key ID    : UUID;
  title     : String;
}
