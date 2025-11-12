# Project Context

## Purpose
SpinalHDL 是一个用于描述数字硬件的语言，它：
- 兼容 EDA 工具，能够生成 VHDL/Verilog 文件
- 比 VHDL、Verilog 和 SystemVerilog 在语法和功能上更强大
- 比传统硬件描述语言更简洁
- 基于 RTL 描述范式，但功能更强大
- 允许使用面向对象编程和函数式编程来设计和验证硬件
- 免费且可在工业环境中无许可证使用

## Tech Stack
- **主要语言**: Scala 2.11.12 (支持多版本: 2.11, 2.12, 2.13)
- **构建工具**: SBT (Scala Build Tool) with sbt-assembly for JAR packaging
- **测试框架**: Scalatest 3.2.14, Scalactic 3.2.10
- **代码格式化**: Scalafmt 3.6.0
- **仿真后端**: Verilator、VCS、XSim、GHDL、IVerilog
- **Python 测试**: Cocotb
- **编译器插件**: 自定义 spinalhdl-idsl-plugin (基于 Scala 编译器扩展)
- **JVM 目标**: Java 1.8 (兼容性)
- **版本管理**: 基于 TypeSafe Config 的动态版本系统

## Project Conventions

### Code Style
- **格式化工具**: Scalafmt
- **最大列宽**: 120 字符
- **对齐预设**: some
- **文档字符串**: 不自动换行 (docstrings.wrap = no)
- **Scala 版本**: 2.12 方言 (runner.dialect = scala212)

### Architecture Patterns
- **模块化设计**: 项目分为多个独立的 Scala 项目
- **核心模块**: `spinal.core` - 硬件描述 DSL
- **标准库**: `spinal.lib` - 辅助设计者的标准库
- **仿真模块**: `spinal.sim` - 与仿真器交互的内部实现
- **测试模块**: `spinal.tester` - 全局/集成测试

#### Stream-Based Design Principles (流式设计原则)

**核心理念**：优先使用反压流（Stream）基础设施，替代传统的状态机设计模式，提高代码的可读性、可维护性和可复用性。

**设计准则**：
1. **Stream 优先原则**：在所有数据流处理场景中优先使用 `spinal.lib.Stream` 基础设施
2. **状态机替代**：使用 `StreamTransactionExtender` 等工具替代复杂的状态机逻辑
3. **流式仲裁**：使用 `StreamArbiter` 进行多流仲裁，确保公平性和效率
4. **流式分离**：使用 `StreamDemux` 等工具进行流分离，减少中间变量
5. **直接转换**：优先使用 `translateWith`、`translateFrom`、`translateInto` 等流转换方法

#### Component Encapsulation Standards (组件封装标准)

**核心原则**：Component类对象的所有输入输出必须通过名为`io`的Bundle实现访问，内部信号不能被外部对象直接访问。

**封装准则**：
1. **IO Bundle强制**：每个Component必须定义`val io = new Bundle { ... }`
2. **信号隔离**：所有外部接口信号必须在`io` Bundle中定义
3. **内部封装**：Component内部信号不得被外部对象直接访问
4. **接口清晰**：通过`io` Bundle提供明确的硬件接口定义

### Testing Strategy
- **测试类型**: 主要是集成测试
- **测试位置**: `tester` 项目中
- **Python 测试**: 使用 Cocotb 进行测试
- **Scala 测试**: 使用 Scalatest 框架
- **并行测试**: 支持并行执行测试 (通过 SBT_TEST_PARALLEL 环境变量控制)
- **测试分类**:
  - 常规测试: `testWithoutFormal` (排除正式验证)
  - 正式验证: `testFormal` (仅运行正式验证测试)
- **测试目录结构**:
  - `src/test/scala/spinal/tester/scalatest/` - Scala 集成测试
  - `src/test/python/` - Cocotb Python 测试
  - `src/test/resources/` - RTL 测试资源文件

### Git Workflow
- **贡献流程**: 先创建 issue 讨论，然后提交 pull request
- **代码审查**: 所有 PR 都需要审查
- **测试要求**: 必须包含单元测试
- **文档要求**: 使用 Scaladoc `/** */` 注释代码
- **发布流程**:
  - 发布到 Maven Central (OSS Sonatype)
  - 生成可执行 JAR: `./release/spinalhdl.jar`
  - 支持本地发布: `sbt +clean +reload +publishSigned`
- **分支管理**:
  - 主开发分支: `dev`
  - 发布版本通过 Git tags 管理
- **代码质量**:
  - 使用 Scalafmt 自动格式化 (diff-ref=dev 基准)
  - 打印格式化差异以便审查

## Domain Context
SpinalHDL 是数字硬件设计领域的高级硬件描述语言，主要特点：
- 生成与 EDA 工具兼容的 RTL 代码
- 支持高级编程范式（OOP、FP）
- 提供类型安全的硬件描述
- 支持参数化设计和代码复用

## Important Constraints
- **许可证**: SpinalHDL 核心使用 LGPL3 许可证，标准库使用 MIT 许可证
- **兼容性**: 必须与主流 EDA 工具兼容
- **性能**: 生成的 RTL 不能有面积/性能开销（相对于手写 VHDL/Verilog）
- **JVM 目标**: 编译目标为 JVM 1.8

## External Dependencies
- **Scala 相关**: scala-library, scala-reflect, scala-compiler
- **测试框架**: scalatest, scalactic
- **工具库**: commons-io, scopt, sourcecode
- **系统库**: affinity (CPU 亲和性), slf4j (日志), oshi-core (系统信息)
- **Python 测试**: cocotb

## Project Structure
```
SpinalHDL/
├── core/           # 核心 DSL (import spinal.core._)
├── lib/            # 标准库 (import spinal.lib._)
├── sim/            # 仿真内部实现
├── tester/         # 测试套件
├── idslplugin/     # Scala 编译器 iDSL 扩展
├── idslpayload/    # idslplugin 的接口
└── scalaplugin/    # 编译器插件
```

## Development Environment
- **IDE 支持**:
  - Metals (VS Code, IntelliJ)
  - IntelliJ IDEA with Scala plugin
  - Scala IDE (Eclipse)
- **构建要求**:
  - Java 8+ (JVM 1.8 target)
  - SBT 1.x
  - 足够的内存 (建议 SBT_OPTS="-Xmx2G")
- **仿真工具**:
  - Verilator (开源，推荐)
  - 商业工具: VCS, ModelSim/Questa, Xcelium
  - 开源工具: GHDL, IVerilog, XSim
- **代码质量工具**:
  - Scalafmt (代码格式化)
  - ScalaUnidoc (API 文档生成)

## Build Configuration
- **构建工具强制要求**: 所有SpinalHDL项目的编译、测试和打包操作必须通过SBT执行，严禁直接使用scalac或java命令
- **Scala 版本**: 2.11.12 (主要), 支持多个 Scala 版本 (2.11, 2.12, 2.13)
- **Java 版本**: 1.8
- **编译选项**: -unchecked, -target:jvm-1.8, -language:reflectiveCalls
- **插件依赖**: spinalhdl-idsl-plugin 编译器插件
- **构建输出**:
  - 核心库: `SpinalHDL-core_*.jar`
  - 标准库: `SpinalHDL-lib_*.jar`
  - 可执行文件: `./release/spinalhdl.jar`
- **项目依赖管理**:
  - 强制 JNA 版本: 5.12.1 (解决兼容性问题)
  - 强制 SLF4J 版本: 2.0.5 (日志接口统一)
- **文档生成**:
  - 使用 ScalaUnidoc 插件生成统一 API 文档
  - 包含 core 和 lib 模块的文档
- **版本配置**:
  - 动态版本系统基于 `project/version.conf`
  - 开发版本标识为 "dev"
  - 支持快照版本 (SNAPSHOT)
