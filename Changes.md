# Changes for ring-middleware-format

## 0.2.0

### Features
  - Chooses format response according to the sort order defined by Accept header (Jani Rahkola)

### Bugfixes
  - Properly lowercases header according to Ring spec (Luke Amdor)
  - Safely handles code for clojure format (Paul M Bauer)
  - safely handle empty request bodies (Philip Aston)
