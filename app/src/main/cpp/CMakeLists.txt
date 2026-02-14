cmake_minimum_required(VERSION 3.10.2)

project("recipebookreader")

# Add your native library
add_library(
    native-lib
    SHARED
    native-lib.cpp  # your C++ source
)

# Include directories if needed
include_directories(${CMAKE_SOURCE_DIR}/include)

# Link libraries (if any)
find_library(
    log-lib
    log
)

target_link_libraries(
    native-lib
    ${log-lib}
)
