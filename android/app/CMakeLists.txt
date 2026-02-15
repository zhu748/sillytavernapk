cmake_minimum_required(VERSION 3.22.1)
project(sillytavern)

add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

add_library(libnode SHARED IMPORTED)
set_target_properties(libnode PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libnode/bin/${ANDROID_ABI}/libnode.so
)

find_library(log-lib log)

# libandroid is required for __android_log_print on some NDK setups.
find_library(android-lib android)

target_include_directories(native-lib PRIVATE ${CMAKE_SOURCE_DIR}/libnode/include/node)

target_link_libraries(native-lib
    libnode
    ${log-lib}
    ${android-lib}
)
