```sqldelight
    DROP TABLE IF EXISTS books;
    CREATE TABLE books (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sha TEXT UNIQUE,
        name TEXT,
        location TEXT UNIQUE,
        author TEXT,
        isbn TEXT DEFAULT NULL,
        scanned INTEGER DEFAULT NULL,
        last_opened INTEGER DEFAULT NULL,
        toc_created INTEGER,
        toc_unavailable INTEGER DEFAULT 0,
        category INTEGER DEFAULT NULL,
        sub_category INTEGER DEFAULT NULL,
        alternate_cover INTEGER DEFAULT 0,
        volume_title INTEGER DEFAULT 0
    );
```
```sqldelight
    DROP TABLE IF EXISTS configuration;
     CREATE TABLE configuration (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        key TEXT,
        json TEXT
       );
    
```


```sqldelight
    DROP TABLE IF EXISTS categories;
    CREATE TABLE categories (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        category TEXT DEFAULT NULL,
        root_location TEXT DEFAULT NULL
	);
    INSERT INTO categories (category) VALUES ('Cookbooks');
    INSERT INTO categories (category, root_location) VALUES ('American', '/storage/emulated/0/Documents/moon/moon/american/');
    INSERT INTO categories (category, root_location) VALUES ('Asian', '/storage/emulated/0/Documents/moon/moon/asian/');
    INSERT INTO categories (category, root_location) VALUES ('British', '/storage/emulated/0/Documents/moon/moon/british/');
    INSERT INTO categories (category, root_location) VALUES ('Burmese', '/storage/emulated/0/Documents/moon/moon/burmese/');
    INSERT INTO categories (category, root_location) VALUES ('Caucasus', '/storage/emulated/0/Documents/moon/moon/caucusus/');
    INSERT INTO categories (category, root_location) VALUES ('Chinese', '/storage/emulated/0/Documents/moon/moon/chinese/');
    INSERT INTO categories (category, root_location) VALUES ('French', '/storage/emulated/0/Documents/moon/moon/french/');
    INSERT INTO categories (category, root_location) VALUES ('Ice cream', '/storage/emulated/0/Documents/moon/moon/ice_cream/');
    INSERT INTO categories (category, root_location) VALUES ('Indian', '/storage/emulated/0/Documents/moon/moon/indian/');
    INSERT INTO categories (category, root_location) VALUES ('Indonesian', '/storage/emulated/0/Documents/moon/moon/indonesian/');
    INSERT INTO categories (category, root_location) VALUES ('Italian', '/storage/emulated/0/Documents/moon/moon/italian/');
    INSERT INTO categories (category, root_location) VALUES ('Japanese', '/storage/emulated/0/Documents/moon/moon/japanese/');
    INSERT INTO categories (category, root_location) VALUES ('Korean', '/storage/emulated/0/Documents/moon/moon/korean/');
    INSERT INTO categories (category, root_location) VALUES ('Malaysian', '/storage/emulated/0/Documents/moon/moon/malaysian/');
    INSERT INTO categories (category, root_location) VALUES ('Mexican', '/storage/emulated/0/Documents/moon/moon/mexican/');
    INSERT INTO categories (category, root_location) VALUES ('Middle East', '/storage/emulated/0/Documents/moon/moon/middle_east/');
    INSERT INTO categories (category, root_location) VALUES ('Other', '/storage/emulated/0/Documents/moon/moon/other/');
    INSERT INTO categories (category, root_location) VALUES ('Spanish', '/storage/emulated/0/Documents/moon/moon/spanish/');
    INSERT INTO categories (category, root_location) VALUES ('Thai', '/storage/emulated/0/Documents/moon/moon/thai/');
    INSERT INTO categories (category, root_location) VALUES ('Vietnamese', '/storage/emulated/0/Documents/moon/moon/vietnamese/');
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
        scale REAL DEFAULT NULL,
        translate REAL DEFAULT NULL,
        bookmark_id INTEGER DEFAULT NULL      
    );
```
```sqldelight
   DROP TABLE IF EXISTS bookmarks;
   CREATE TABLE bookmarks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id_fk INTEGER DEFAULT NULL,
    title TEXT NOT NULL,
    page INTEGER NOT NULL,
    offset REAL DEFAULT NULL,
    scale REAL NOT NULL,
    translate REAL NOT NULL,
    is_image INTEGER DEFAULT 0,
    unique_key TEXT UNIQUE
);
```

### The book history
```sqldelight
    DROP TABLE IF EXISTS history;
    CREATE TABLE history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        book_id_fk INTEGER DEFAULT NULL,
        page INTEGER,
        offset INTEGER,
        translation_x REAL,
        scale REAL
    );
```
### The conversion table
```sqldelight
    DROP TABLE IF EXISTS weight_chart;
    CREATE TABLE weight_chart (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ingredient TEXT NOT NULL,
        volume TEXT NOT NULL,
        ounces TEXT NOT NULL,
        grams TEXT NOT NULL
    );
```

```sqldelight
SELECT b.name, c.category AS main_category, sc.category AS sub_category
FROM  books AS b
LEFT JOIN  categories AS c
ON c.id = b.category

LEFT JOIN  categories AS sc
ON sc.id = b.sub_category

WHERE b.id IS NOT NULL AND c.category = 'Cookbooks'
ORDER BY (b.sub_category IS NULL) ASC, b.sub_category ASC, (b.category  IS NULL) ASC, b.category  ASC;


```

## get a list of categories is use

```sqldelight
SELECT c.category AS used_categories
FROM books AS b
LEFT JOIN categories AS c
ON c.id = b.category
WHERE c.category IS NOT NULL
UNION
SELECT sc.category
FROM books AS b
LEFT JOIN categories AS sc
ON sc.id = b.sub_category
WHERE sc.category IS NOT NULL
ORDER BY category;

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