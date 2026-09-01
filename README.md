# Recipe Book Reader

**Recipe Book Reader** is a free, offline **Android PDF reader / PDF viewer app** for browsing large
PDF documents that have a table of contents — cookbooks, manuals, textbooks, or any other PDF, not
just recipe books.

It's built on [**MuPDF**](https://mupdf.com/) (the `com.artifex.mupdf:fitz` library), the same
rendering engine used by Artifex's
own [mupdf-android-viewer](https://github.com/ArtifexSoftware/mupdf-android-viewer). On top of that
core PDF renderer it adds a bookshelf, a browsable/searchable table of contents (including images),
bookmarks, in-document text search, and reading history; the things you actually need when your PDF
library is a few hundred pages long, and you want to find your way back to a page later.

## Features

- Fast PDF rendering via MuPDF (`fitz`).
- Bookshelf / recent books, browsable by author or book name.
- Full table of contents navigation, including TOC entries generated for images (see [recipe-book-reader-companion](https://github.com/hurnell/recipe-book-reader-companion)).
- Bookmarks per book (as well as for all books).
- Full-text search inside a PDF.
- Reading history — return to your last position in any book.
- Category and metadata management (e.g. filed by cookbook/subject category).
- Weight and temperature unit conversion for selected text (handy for recipes, but works on any
  numeric text).

## Download

Grab the latest APK from
the [Releases page](https://github.com/hurnell/recipe-book-reader/releases/latest).

## Available views (left side navigation screen):

### Recent Books

- Simple list of books ordered by last opened.
- Long click on book cover to open [Edit Book Overlay](#edit-book-overlay).
- Short click opens book.

### Book Shelf

- Filter by category (dropdown on top right).
- Long click on book cover to open [Edit Book Overlay](#edit-book-overlay).
- Short click opens book.

### Book Author or Name

- Search by author or book name (toggle icon).
- Long click on book cover to open [Edit Book Overlay](#edit-book-overlay).
- Short click opens book.

### Browse Files

- This is the starting point!
- Click on file and wait for table of contents to be read.
- Long click on book cover to open [Edit Book Overlay](#edit-book-overlay).
- Short click opens book.

### Search Table of Contents

- Enter search term in text field and click on search icon.
- Filter by category (dropdown on top right) also available.
- Long click on title shows full title.
- Short click opens book at chosen recipe.

### Bookmarks

- Complete list of bookmarks.
- Filter by category (dropdown on top right) also available.
- Long click on title shows full bookmark text.
- Short click opens book at bookmark.

### Recent Recipes

- List of recently viewed recipes.
- Entries can be deleted with x icon.
- Long click on title shows full recipe title text.
- Short click opens book at chosen recipe.

### Edit Book Overlay

- Edit book name, author, ISBN, category and sub category.
- Edit book cover.
- Delete book from library (and optionally delete from device).
- Toggle whether to show popup of table of contents text when pressing volume up and down to
  navigate to the
  previous/next entry.

| Icon                                                                     | Action                                                                         |
|--------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| <img src="help/edit_icon.png" width="30" style="border:1px solid #000;" alt="table of contents icon">   | (Start) editing name, author, ISBN, category and sub category.                 |
| <img src="help/save_icon.png" width="30" style="border:1px solid #000;" alt="table of contents icon">   | Save/(Complete editing name, author, ISBN, category and sub category.)         |
| <img src="help/cancel_icon.png" width="30" style="border:1px solid #000;" alt="table of contents icon"> | Cancel editing name, author, ISBN, category and sub category.)                 |
| <img src="help/plus_icon.png" width="30" style="border:1px solid #000;" alt="back icon">                | Add sub category.                                                              |
| <img src="help/cover_search_icon.png" width="30" style="border:1px solid #000;" alt="expand icon">      | Search for book cover on device.                                               |
| <img src="help/internet_search_icon.png" width="30" style="border:1px solid #000;" alt="expand icon">   | Search for book cover over internet.                                           |
| <img src="help/return_to_saved_icon.png" width="30" style="border:1px solid #000;" alt="expand icon">   | Return to saved book cover after replacing with local or internet book cover*. |
| <img src="help/reload_cover_icon.png" width="30" style="border:1px solid #000;" alt="expand icon">      | Reload book cover from first page of book**.                                   |
| <img src="help/search_lost_icon.png" width="30" style="border:1px solid #000;" alt="expand icon">       | Search for lost book cover from saved local covers**.                          |
| <img src="help/delete_icon.png" width="30" style="border:1px solid #000;" alt="link icon">              | Delete book or sub category.                                                   |

- Note* - only available if you have substituted book cover for local image or internet image.
- Note** - only available if you have changed the sha for a book in the local database (edge case).

### Single book view

- Click on volume up or down to navigate to previous/next toc entry. If
  configured (see [Edit Book Overlay](#edit-book-overlay)) then the toc title will pop up.
- From Table of Contents list long click on entry adds (or offers option to removes) entry to
  bookmarks. Short click goes to page.
- From Bookmarks list short click goes to bookmarked page. Click on bookmark icon shows option to
  remove bookmark.

| Icon                                                                  | Action                                                                                                               |
|-----------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| <img src="help/expand_icon.png" width="30" style="border:1px solid #000;" alt="expand icon">         | Shows/hides the top and bottom navigation bars.                                                                      |
| <img src="help/link_icon.png" width="30" style="border:1px solid #000;" alt="link icon">             | Toggles links and popups — when red prevents unwanted pop-ups and links - when bookmark single click can create one. |
| <img src="help/toc_icon.png" width="30" style="border:1px solid #000;" alt="table of contents icon"> | Shows the table of contents for the book.                                                                            |
| <img src="help/bookmarks_icon.png" width="30" style="border:1px solid #000;" alt="bookmarks icon">   | Shows any bookmarks you've made in the book (only shown if you have some).                                           |
| <img src="help/back_icon.png" width="30" style="border:1px solid #000;" alt="back icon">             | Returns to the previous screen.                                                                                      |
| <img src="help/search_icon.png" width="30" style="border:1px solid #000;" alt="search icon">         | Opens a screen to search for text inside the PDF.                                                                    |

## Preparing PDFs (optional)

If you want a book's table of contents to be as useful as possible inside the app (e.g. TOC entries
for recipe/image pages), see the companion
repo: [recipe-book-reader-companion](https://github.com/hurnell/recipe-book-reader-companion). It
has scripts to prepare a PDF's table of contents and metadata before loading it onto your phone.

## Development / debugging

### Inspect the on-device sqlite database

### preload sqlite executable***

```shell
adb shell chmod 777 /data/local/tmp
adb shell mkdir /data/local/tmp/tools
adb shell chmod 777 /data/local/tmp/tools
adb push tools/sqlite3 /data/local/tmp/tools/sqlite3
```

### Commands to enter database***

```shell
adb shell #then
run-as apk.hurnell.recipebookreader

  ./files/sqlite3 ./databases/recipe-reader.db
```

### Get copy of current database locally***:
```shell
# ensure app is stopped
adb shell am force-stop apk.hurnell.recipebookreader
# get copy of database 
adb exec-out run-as apk.hurnell.recipebookreader cat databases/recipe-reader.db > recipe-reader.db
```
Note*** these adb commands will only be available if you install the app yourself (not via released apk)

### Updating PDF metadata locally

(Before uploading to phone — note if you change the metadata it's best to also change the name of
the file on the phone as well.)

```shell
brew install exiftool

exiftool -Keywords file.pdf # get current metadata keywords
exiftool file.pdf # get all current metadata
exiftool -Keywords="main_category=Cookbooks, sub1_category=Sweets , sub2_category=Desserts" file.pdf # update metadata keywords
```

Note: You can install and use exiftool on your phone but only through Termux (which is beyond the
scope of this tutorial).
