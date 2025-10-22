# SpinalHDL Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **DfiDdrPhy Component**: New multi-standard DDR PHY component with DFI 3.1 compliance
  - Support for DDR2, DDR3, DDR4, LPDDR2, LPDDR3, LPDDR4 standards
  - Complete DFI 3.1 interface implementation including all interface groups
  - Frequency ratio support (1:1, 1:2, 1:4)
  - Advanced features: DBI, CRC, CA Parity, training interfaces
  - Modular architecture with comprehensive testing and documentation

### Changed
- Enhanced DFI infrastructure with new PHY integration capabilities
- Updated documentation with DfiDdrPhy usage examples and API reference

### Fixed
- Improved SpinalHDL compilation stability for complex memory components

## [1.10.1] - 2024-12-15

### Added
- New memory controller components
- Enhanced simulation capabilities

### Changed
- Updated build system and dependencies

### Fixed
- Various bug fixes and performance improvements

## [1.10.0] - 2024-11-20

### Added
- Major memory subsystem enhancements
- New DFI (DDR PHY Interface) implementation
- Comprehensive testing framework improvements

### Changed
- Refactored core memory components for better modularity
- Updated API for memory controllers

### Fixed
- Memory timing and initialization issues
- Improved compatibility with various DDR standards

## [1.9.4] - 2024-10-01

### Added
- Enhanced debugging capabilities
- New utility functions for memory operations

### Fixed
- Critical timing issues in memory controllers
- Improved error handling in simulation

## [1.9.3] - 2024-09-15

### Added
- Initial DFI infrastructure components
- Basic DDR controller implementations

### Changed
- Restructured memory package organization

### Fixed
- Compilation issues with new memory components

---

## Types of changes
- `Added` for new features
- `Changed` for changes in existing functionality
- `Deprecated` for soon-to-be removed features
- `Removed` for now removed features
- `Fixed` for any bug fixes
- `Security` in case of vulnerabilities