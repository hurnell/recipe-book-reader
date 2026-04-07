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
        normalised_title TEXT,
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
    normalised_title TEXT DEFAULT NULL,
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
        grams TEXT NOT NULL,
        decimal_cups REAL DEFAULT NULL
    );
    INSERT INTO "weight_chart" VALUES(NULL,'''00'' Pizza Flour','1 cup','4','116',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Agave syrup','1/4 cup','3','84',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'All-Purpose Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Almond butter','1/4 cup','2 1/3','68',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Almond Flour','1 cup','3 3/8','96',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Almond meal','1 cup','3','84',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Almond paste (packed)','1 cup','9 1/8','259',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Almonds (sliced)','1/2 cup','1 1/2','43',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Almonds (slivered)','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Almonds, whole (unblanched)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Amaranth flour','1 cup','3 5/8','103',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Apple juice concentrate','1/4 cup','2 1/2','70',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Apples (dried, diced)','1 cup','3','85',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Apples (peeled, sliced)','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Applesauce','1 cup','9','255',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Apricots (dried, diced)','1/2 cup','2 1/4','64',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Artisan Bread Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Artisan Bread Topping','1/4 cup','1 1/2','43',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Baker''s Cinnamon Filling','1 cup','5 3/8','152',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Baker''s Fruit Blend','1 cup','4 1/2','128',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Baker''s Special Sugar (superfine sugar, castor sugar)','1 cup','6 3/4','190',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Baking powder','1 teaspoon','','4',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Baking soda','1/2 teaspoon','','3',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Baking Sugar Alternative','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Bananas (mashed)','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Barley (cooked)','1 cup','7 5/8','215',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Barley (pearled)','1 cup','7 1/2','213',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Barley flakes','1/2 cup','1 5/8','46',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Barley flour','1 cup','3','85',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Barley malt syrup','2 tablespoons','1 1/2','42',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Basil pesto','2 tablespoons','1','28',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Bell peppers (fresh)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Berries (frozen)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Better Cheddar Cheese Powder','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Blueberries (dried)','1 cup','5 1/2','156',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Blueberries (fresh or frozen)','1 cup','5 to 6','140 to 170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Blueberry juice','1 cup','8 1/2','241',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Boiled cider','1/4 cup','3','85',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Bran cereal','1 cup','2 1/8','60',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Bread crumbs (dried)','1/4 cup','1','28',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Bread crumbs (fresh)','1/4 cup','3/4','21',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Bread crumbs (Japanese Panko)','1 cup','1 3/4','50',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Bread Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Brown rice (cooked)','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Brown rice flour','1 cup','4 1/2','128',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Brown sugar (dark or light, packed)','1 cup','7 1/2','213',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Buckwheat (whole)','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Buckwheat Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Bulgur','1 cup','5 3/8','152',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Butter','8 tablespoons (1/2 cup)','4','113',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Unsalted Butter','8 tablespoons (1/2 cup)','4','113',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Buttermilk','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Buttermilk powder','2 tablespoons','2/3','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Cacao nibs','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cake Enhancer','2 tablespoons','1/2','14',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Candied Lemon Peel','1/4 cup','1 1/3','37',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Candied Orange Peel','1/4 cup','7/8','25',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Caramel (14-16 individual pieces, 1" squares)','1/2 cup','5','142',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Caramel bits (chopped Heath or toffee)','1 cup','5 1/2','156',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Caraway seeds','2 tablespoons','5/8','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Carrots (cooked and puréed)','1/2 cup','4 1/2','128',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Carrots (diced)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Carrots (grated)','1 cup','3 1/2','99',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cashews (chopped)','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cashews (whole)','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Celery (diced)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cheese (Feta)','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Cheese (grated cheddar, jack, mozzarella, or Swiss)','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cheese (grated Parmesan)','1/2 cup','1 3/4','50',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Cheese (Ricotta)','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cherries (candied)','1/4 cup','1 3/4','50',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Cherries (dried)','1/2 cup','2 1/2','71',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Cherries (fresh, pitted, chopped)','1/2 cup','2 7/8','80',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Cherries (frozen)','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cherry Concentrate','2 tablespoons','1 1/2','42',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Chia seeds','1/4 cup','1 1/3','37',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Chickpea flour','1 cup','3','85',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Chives (fresh)','1/2 cup','3/4','21',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Chocolate (chopped)','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Chocolate Chips','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Chocolate Chunks','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cinnamon Sweet Bits','1/4 cup','1 1/4','35',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Cinnamon-Sugar','1/4 cup','1 3/4','50',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Climate Blend Flour','1 cup','4','115',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cocoa (unsweetened)','1/2 cup','1 1/2','42',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut (sweetened, shredded)','1 cup','3','85',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut (toasted)','1 cup','3','85',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut (unsweetened, desiccated)','1 cup','3','85',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut (unsweetened, large flakes)','1 cup','2 1/8','60',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut (unsweetened, shredded)','1 cup','1 7/8','53',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut cream(unsweetened)','1 cup','10','284',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut Flour','1 cup','4 1/2','128',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut milk; canned, well shaken','1 cup','8 1/2','241',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut milk(evaporated)','1 cup','8 1/2','242',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut Milk Powder','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut oil','1/2 cup','4','113',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Coconut sugar','1/2 cup','2 3/4','77',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Confectioners'' sugar (unsifted)','2 cups','8','227',2.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cookie butter','1/4 cup','2 1/2','72',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Cookie crumbs','1 cup','3','85',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Corn (fresh or frozen)','1/4 cup','1 1/3','38',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Corn (popped)','4 cups','3/4','21',4.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Corn syrup','1 cup','11','312',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cornmeal (whole)','1 cup','4 7/8','138',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cornmeal (yellow, Quaker)','1 cup','5 1/2','156',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cornstarch','1/4 cup','1','28',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Cracked wheat','1 cup','5 1/4','149',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cranberries (dried)','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Cranberries (fresh or frozen)','1 cup','3 1/2','99',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cream (heavy cream, light cream, or half & half)','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cream cheese','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Cream of coconut','1/2 cup','5','142',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Crème fraiche','1/2 cup','4 1/3','124',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Crystallized ginger','1/2 cup','3 1/4','92',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Currants','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Dates (chopped)','1 cup','5 1/4','149',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Demerara sugar','1 cup','7 3/4','220',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Dried Blueberry Powder','1/4 cup','1','28',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Dried milk (Baker''s Special Dry Milk)','1/4 cup','1','28',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Dried nonfat milk (powdered)','1/4 cup','1','28',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Dried potato flakes (instant mashed potatoes)','1/2 cup','1 1/2','43',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Dried whole milk (powdered)','1/2 cup','1 3/4','50',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Durum Flour','1 cup','4 3/8','124',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Easy Roll Dough Improver','2 tablespoons','5/8','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Egg (fresh)','1 large','1 3/4','50',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Egg white (fresh)','1 large','1 to 1 1/4','30 to 35',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Egg whites (dried)','2 tablespoons','3/8','11',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Egg yolk (fresh)','1 large','1/2','14',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Espresso Powder','1 tablespoon','1/4','7',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Everything Bagel Topping','1/4 cup','1 1/4','35',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Figs (dried, chopped)','1 cup','5 1/4','149',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'First Clear Flour','1 cup','3 3/4','106',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Flax meal','1/2 cup','1 3/4','50',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Flaxseed','1/4 cup','1 1/4','35',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Formaggio Italiano Cheese and Herb Blend','1/4 cup','1','30',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'French-Style Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Fruitcake Fruit Blend','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Garlic (cloves, in skin for roasting)','1 large head','4','113',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Garlic (minced)','2 tablespoons','1','28',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Garlic (peeled and sliced)','1 cup','5 1/4','149',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Ghee','1/4 cup','1 1/2','44',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Ginger (fresh, sliced)','1/4 cup','2','57',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Gluten-Free Pizza Flour','1 cup','3 1/2','100',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Gluten-Free All-Purpose Baking Mix','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Gluten-Free All-Purpose Flour','1 cup','5 1/2','156',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Gluten-Free Bread Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Gluten-Free Measure for Measure Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Gluten-Free sourdough starter (made with Gluten-Free Bread Flour)','1 cup','6 to 6 1/2','170 to 185',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Gluten-Free sourdough starter (made with Gluten-Free Measure for Measure Flour)','1 cup','8 to 8 1/2','227 to 241',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Glutinous rice flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Golden Wheat Flour','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Graham cracker crumbs (store-bought or crushed from whole)','1 cup (6 to 7 crackers)','3 1/2','100',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Granola','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Grape Nuts','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Guava paste','1/4 cup','3 1/2','100',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Harvest Grains Blend','1/2 cup','2 5/8','74',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Hazelnut flour','1 cup','3 1/8','89',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Hazelnut Praline Paste','1/2 cup','5 1/2','156',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Hazelnut spread','1/2 cup','5 5/8','160',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Hazelnuts (whole)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Hi-Maize Natural Fiber','1/4 cup','1 1/8','32',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'High-Gluten Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Honey','1 tablespoon','3/4','21',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Instant ClearJel','1 tablespoon','3/8','11',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Irish-Style Flour','1 cup','3 7/8','110',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Italian-Style Flour','1 cup','3 3/4','106',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Jam or preserves','1/4 cup','3','85',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Jammy Bits','1 cup','6 1/2','184',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Keto Wheat Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Keto Wheat Pizza Crust Mix','1 cup','3 7/8','110',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Key Lime Juice','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Lard','1/2 cup','4','113',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Leeks (diced)','1 cup','3 1/4','92',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Lemon Crumbles','1 cup','6 1/3','180',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Lemon Curd','1/2 cup','4','113',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Lemon juice','1 tablespoon','','14',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Lemon Juice Powder','2 tablespoons','5/8','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Lime Juice Powder','2 tablespoons','5/8','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Macadamia nuts (whole)','1 cup','5 1/4','149',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Malt syrup','2 tablespoons','1 1/2','43',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Malted Milk Powder','1/4 cup','1 1/4','35',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Malted Wheat Flakes','1/2 cup','2 1/4','64',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Maple sugar','1/2 cup','2 3/4','78',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Maple syrup','1/2 cup','5 1/2','156',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Marshmallow spread, homemade','1 cup','2 1/2','72',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Marshmallow spread, store-bought','1 cup','4 1/3','123',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Marshmallow Fluff®','1 cup','4 1/2','128',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Marshmallows (mini)','1 cup','1 1/2','43',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Marzipan','1 cup','10 1/8','290',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Masa Harina','1 cup','3 1/4','93',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Mascarpone cheese','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Mashed potatoes','1 cup','7 1/2','213',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Mashed sweet potatoes','1 cup','8 1/2','240',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Mayonnaise','1/2 cup','4','113',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Medium Rye Flour','1 cup','3 3/4','106',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Meringue powder','1/4 cup','1 1/2','43',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Milk (evaporated)','1/2 cup','4','113',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Milk (fresh)','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Millet (whole)','1/2 cup','3 5/8','103',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Mini chocolate chips','1 cup','6 1/4','177',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Molasses','1/4 cup','3','85',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Mushrooms (sliced)','1 cup','2 3/4','78',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Non-Diastatic Malt Powder','2 tablespoons','5/8','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Nutella','1/2 cup','5 1/4','149',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Oat bran','1/2 cup','1 7/8','53',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Oat Flour','1 cup','3 1/4','92',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Oats (King Arthur Rolled)','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Oats (old-fashioned or quick-cooking)','1 cup','3 1/8','89',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Oats (prepared)','1 cup','5 1/8','147',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Olive oil','1/4 cup','1 3/4','50',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Olives (sliced)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Onions (fresh, diced)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Paleo Baking Flour','1 cup','3 5/8','104',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Palm shortening','1/4 cup','1 1/2','45',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Passion fruit purée','1/3 cup','2 1/8','60',0.3333);
    INSERT INTO "weight_chart" VALUES(NULL,'Pasta Flour Blend','1 cup','5 1/8','145',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pastry Flour','1 cup','3 3/4','106',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pastry Flour Blend','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Peaches (peeled and diced)','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Peanut butter','1/2 cup','4 3/4','135',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Peanuts (whole, shelled)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pears (peeled and diced)','1 cup','5 3/4','163',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pecan Meal','1 cup','2 3/4','80',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pecans (diced)','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Pecans (whole)','1 cup','3 3/4','105',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pie Filling Enhancer','1/4 cup','1 5/8','46',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Pine nuts','1/2 cup','2 1/2','71',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Pineapple (crushed, drained)','1 cup','9','256',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pineapple (dried)','1/2 cup','2 1/2','71',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Pineapple (fresh or canned, diced)','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pistachio nuts (shelled)','1/2 cup','2 1/8','60',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Pistachio Paste','1/4 cup','2 3/4','78',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Pizza Dough Flavor','2 tablespoons','','12',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Pizza Flour Blend','1 cup','4 3/8','124',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pizza sauce','1/4 cup','2','57',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Pizza Seasoning','2 tablespoons','1/3','10',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Polenta (coarse ground cornmeal)','1 cup','5 3/4','163',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Poppy seeds','2 tablespoons','5/8','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Potato Flour','1/4 cup','1 5/8','46',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Potato starch','1 cup','5 3/8','152',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pumpernickel Flour','1 cup','3 3/4','106',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Pumpkin purée','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Queso fresco','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Quinoa (cooked)','1 cup','6 1/2','184',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Quinoa (whole)','1 cup','6 1/4','177',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Quinoa flour','1 cup','3 7/8','110',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Raisins (loose)','1 cup','5 1/4','149',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Raisins (packed)','1/2 cup','3','85',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Raspberries (fresh)','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Rhubarb (sliced, 1/2" slices)','1 cup','4 1/4 to 5','120 to 140',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Rice (long grain, dry)','1/2 cup','3 1/2','99',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Rice flour (white)','1 cup','5','142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Rice Krispies','1 cup','1','28',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Rye Bread Improver','2 tablespoons','','14',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Rye Chops','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Rye flakes','1 cup','4 3/8','124',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Rye Flour Blend','1 cup','3 3/4','106',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Salt (Kosher, Diamond Crystal)','1 tablespoon','','8',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Salt (Kosher, Morton''s)','1 tablespoon','','16',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Salt (table)','1 tablespoon','','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Scallions (sliced)','1 cup','2 1/4','64',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Self-Rising Flour','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Semolina Flour','1 cup','5 3/4','163',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sesame seeds','1/2 cup','2 1/2','71',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Shallots (peeled and sliced)','1 cup','5 1/2','156',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Six-Grain Blend','1 cup','4 1/2','128',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Snow White Non-Melting Topping Sugar','1/2 cup','2','57',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Sorghum flour','1 cup','4 7/8','138',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sour cream','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sourdough starter','1 cup','8 to 8 1/2','227 to 241',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Soy flour','1/4 cup','1 1/4','35',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Sparkling Sugar','1/4 cup','2','57',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Spelt Flour','1 cup','3 1/2','99',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sprouted Wheat Flour','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Steel cut oats','1/2 cup','2 1/2','70',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Sticky Bun Sugar','1 cup','3 1/2','99',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Strawberries (fresh sliced)','1 cup','5 7/8','167',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sugar (granulated white)','1 cup','7','198',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sugar substitute (Splenda)','1 cup','7/8','25',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sundried tomatoes (dry pack)','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sunflower seeds','1/4 cup','1 1/4','35',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Super 10 Blend','1 cup','3 3/4','106',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Swedish Pearl Sugar','1/4 cup','1 3/4','49',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Sweet Ground Chocolateand Cocoa Blend','1/4 cup','1','28',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Sweetened condensed coconut milk','1 cup','10 1/8','288',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Sweetened condensed milk','1/4 cup','2 3/4','78',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Tahini paste','1/2 cup','4 1/2','128',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Tapioca starch or flour','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Tapioca (quick cooking)','2 tablespoons','3/4','21',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Teff flour','1 cup','4 3/4','135',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'The Works Bread Topping','1/4 cup','1 1/4','35',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Toasted Almond Flour','1 cup','3 3/8','96',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Toffee chunks','1 cup','5 1/2','156',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Tomato paste','2 tablespoons','1','29',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Tropical Fruit Blend','1 cup','4 1/2 to 5','128 to 142',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Turbinado sugar (raw)','1 cup','6 3/8','180',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Unbleached Cake Flour','1 cup','4 1/4','120',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Vanilla Extract','1 tablespoon','1/2','14',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Vegetable oil','1 cup','7','198',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Vegetable shortening','1/4 cup','1 5/8','46',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'Vital Wheat Gluten','2 tablespoons','5/8','18',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Walnuts (chopped)','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Walnuts (whole)','1/2 cup','2 1/4','64',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Water','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Wheat berries (red)','1 cup','6 1/2','184',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Wheat bran','1/2 cup','1 1/8','32',0.5);
    INSERT INTO "weight_chart" VALUES(NULL,'Wheat germ','1/4 cup','1','28',0.25);
    INSERT INTO "weight_chart" VALUES(NULL,'White Chocolate Chips','1 cup','6','170',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'White Rye Flour','1 cup','3 3/4','106',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'White Whole Wheat Flour','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Whole Grain Flour Blend','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Whole Wheat Flour (Premium 100%)','1 cup','4','113',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Whole Wheat Pastry Flour / Graham Flour','1 cup','3 3/8','96',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Yeast (instant)','2 teaspoons','1/5','6',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Yeast (instant)','2 1/4 teaspoons','1/4','7',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Yeast (instant)','1 tablespoon','1/3','9',NULL);
    INSERT INTO "weight_chart" VALUES(NULL,'Yogurt','1 cup','8','227',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Yuletide Cheer Fruit Blend','1 cup','4 1/2','130',1.0);
    INSERT INTO "weight_chart" VALUES(NULL,'Zucchini (shredded)','1 cup','4 1/4 to 5 1/4','121 to 150',1.0);

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