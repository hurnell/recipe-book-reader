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
        category TEXT DEFAULT NULL,
        sub_category TEXT DEFAULT NULL
    );
```

### count toc per book
```sqldelight
SELECT b.name, COUNT(t.id) 
FROM toc AS t 
LEFT JOIN books AS b
ON t.book_id_fk = b.id;
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

```shell
adb shell "run-as apk.hurnell.recipebookreader cp /data/data/apk.hurnell.recipebookreader/databases/recipe-reader.db /sdcard/recipe-reader.db"
adb pull /storage/emulated/0/Android/data/apk.hurnell.recipebookreader/files/recipe-reader.db
open recipe-reader.db
```