```sqldelight
    DROP TABLE IF EXISTS books;
    CREATE TABLE books (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sha TEXT,
        name TEXT,
        location TEXT,
        author TEXT,
        last_opened INTEGER,
        toc_created INTEGER,
        category TEXT,
        sub_category TEXT
    );
```
```sqldelight
    DROP TABLE IF EXISTS toc;
    CREATE TABLE toc (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        book_id_fk INTEGER,
        parent_id INTEGER,
        level INTEGER,
        title TEXT,
        page INTEGER,
        offset REAL,
        scale REAL,
        translate REAL
    );
```
```sqldelight
    DROP TABLE IF EXISTS bookmarks;
    CREATE TABLE bookmarks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        book_id_fk INTEGER,
        title TEXT,
        page INTEGER,
        offset REAL,
        scale REAL,
        translate REAL
    );
```