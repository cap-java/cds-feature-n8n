namespace test;

entity Items {
  key ID    : UUID;
  title     : String;
  status    : String;
}

entity Orders {
  key ID    : UUID;
  total     : Integer;
}
