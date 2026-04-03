use std::{env, fs, path::Path};

fn main() {
    let out_dir = env::var("OUT_DIR").unwrap();
    let dest_path = Path::new(&out_dir).join("generated_type_tests.rs");

    // Generate tests from shared spec if Kotlin entries exist, otherwise empty module.
    let code = type_test_spec::generate_test_code("kotlin");
    fs::write(&dest_path, code).unwrap();

    println!("cargo:rerun-if-changed=../../type_serialization_tests.md");
}
