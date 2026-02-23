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
        toc_unavailable INTEGER DEFAULT 0,
        category TEXT DEFAULT "Cookery",
        sub_category TEXT DEFAULT NULL,
        alternate_cover TEXT DEFAULT NULL
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
        offset REAL DEFAULT NULL,
        scale REAL DEFAULT NULL,
        translate REAL DEFAULT NULL      
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
## OPEN ASSETS DATABASE
```shell
open app/src/main/assets/recipe-reader.db
```

## GET AND READ ACTUAL DATABASE
```shell
cd ~/Documents/recipe_reader
adb shell "run-as apk.hurnell.recipebookreader cp /data/data/apk.hurnell.recipebookreader/databases/recipe-reader.db /sdcard/recipe-reader.db"
adb pull /sdcard/recipe-reader.db
open recipe-reader.db
```