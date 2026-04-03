use baml_types::baml_value::TypeLookups;
use dir_writer::IntermediateRepr;

/// Tracks which Kotlin package we are currently rendering into.
///
/// Kotlin uses dot-separated package names (e.g. `com.boundaryml.baml_client.types`).
/// The package controls import resolution: types in the same package need no prefix,
/// types in a different package need the fully-qualified name or an import.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Package {
    package_path: Vec<String>,
    /// If true, this package is always imported (e.g. `com.boundaryml.baml.*`)
    /// and types from it never need a package prefix.
    imported: bool,
}

impl Package {
    fn new(package: &str) -> Self {
        let parts: Vec<_> = package.split('.').map(|s| s.to_string()).collect();
        if parts.is_empty() {
            panic!("Package cannot be empty");
        }
        Package {
            package_path: parts,
            imported: false,
        }
    }

    /// Create a package that is always imported (no prefix needed).
    fn imported(package: &str) -> Self {
        let mut pkg = Self::new(package);
        pkg.imported = true;
        pkg
    }

    /// Returns how to reference this package from another package.
    /// In Kotlin, types in the same package need no qualifier.
    /// Types in different packages use qualified names.
    /// Types from imported packages (SDK classes like StreamState, Checked)
    /// are always available without prefix since they're star-imported.
    pub fn relative_from(&self, other: &CurrentRenderPackage) -> String {
        // Imported packages are always in scope (via `import com.boundaryml.baml.*`)
        if self.imported {
            return "".to_string();
        }
        let other = other.get();
        if self.package_path == other.package_path {
            return "".to_string();
        }
        // Kotlin requires fully-qualified names for cross-package references
        format!("{}.", self.package_path.join("."))
    }

    #[allow(dead_code)]
    pub fn current(&self) -> String {
        self.package_path.last().unwrap().clone()
    }

    pub fn types() -> Package {
        Package::new("baml_client.types")
    }

    pub fn stream_types() -> Package {
        Package::new("baml_client.stream_types")
    }

    pub fn checked() -> Package {
        Package::types()
    }

    pub fn stream_state() -> Package {
        Package::imported("com.boundaryml.baml")
    }
}

impl std::fmt::Display for Package {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.package_path.join("."))
    }
}

#[derive(Clone)]
pub(crate) struct CurrentRenderPackage {
    package: std::sync::Arc<std::sync::Mutex<std::sync::Arc<Package>>>,
    lookup: std::sync::Arc<IntermediateRepr>,
}

impl CurrentRenderPackage {
    pub fn new(package: &str, lookup: std::sync::Arc<IntermediateRepr>) -> Self {
        Self {
            package: std::sync::Arc::new(std::sync::Mutex::new(std::sync::Arc::new(Package::new(
                package,
            )))),
            lookup,
        }
    }

    pub fn lookup(&self) -> &impl TypeLookups {
        self.lookup.as_ref()
    }

    pub fn get(&self) -> std::sync::Arc<Package> {
        self.package.lock().unwrap().clone()
    }

    pub fn set(&self, package: &str) {
        match self.package.lock() {
            Ok(mut orig) => {
                *orig = std::sync::Arc::new(Package::new(package));
            }
            Err(e) => {
                panic!("Failed to get package: {e}");
            }
        }
    }

    #[allow(dead_code)]
    pub fn name(&self) -> String {
        self.get().package_path.last().unwrap().clone()
    }

    #[allow(dead_code)]
    pub fn namespace(&self) -> String {
        match self.name().as_str() {
            "types" => "CFFITypeNamespace.TYPES".to_string(),
            "stream_types" => "CFFITypeNamespace.STREAM_TYPES".to_string(),
            other => panic!("Invalid package for a namespace call: {other}"),
        }
    }
}
