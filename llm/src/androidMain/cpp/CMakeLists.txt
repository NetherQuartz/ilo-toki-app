cmake_minimum_required(VERSION 3.22.1)
project("ilotoki_llm")

# Resolve the vendored llama.cpp checkout and the shared C core to absolute paths
get_filename_component(LLAMA_DIR "../../../../llama.cpp" ABSOLUTE)
get_filename_component(CORE_DIR "../../nativeCommon/cpp" ABSOLUTE)

# llama.cpp's own build. Hand-listing its sources used to be viable, but upstream
# now splits every model architecture into its own translation unit (140+ files),
# so the list rotted on the first submodule update. Delegating keeps us honest.
set(BUILD_SHARED_LIBS       ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER      OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL              OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP             OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE             OFF CACHE BOOL "" FORCE)

# Build every Android CPU baseline upstream knows about and let ggml dlopen the
# best match at startup, instead of picking a variant by hand at load time.
set(GGML_BACKEND_DL         ON  CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS   ON  CACHE BOOL "" FORCE)

# Not EXCLUDE_FROM_ALL: the CPU backend variants are dlopen'ed, so nothing links
# against them and they would never get built (or packaged into the APK).
add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

add_library(ilotoki_llm SHARED
        ${CORE_DIR}/ilotoki_llm.cpp
        jni.cpp
)

target_include_directories(ilotoki_llm PRIVATE ${CORE_DIR})

target_compile_options(ilotoki_llm PRIVATE
        -O3
        -fvisibility=hidden -fvisibility-inlines-hidden
        -ffunction-sections -fdata-sections
)

target_link_libraries(ilotoki_llm PRIVATE llama ggml log)
