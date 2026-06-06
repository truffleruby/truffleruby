# Using the IntelliJ IDE for Development

IntelliJ is one of the best supported IDE for editing TruffleRuby.

It provides a real polyglot editor experience by having great support for Java,
Ruby and Python all in one IDE. For instance, you can jump from primitives in
Ruby code to the corresponding Java class in IntelliJ.

## The Preferences/Settings Menu

Note that the global IntelliJ settings are accessed differently based on the platform.
* On macOS, it's under `IntelliJ IDEA` -> `Settings`
* On Linux, it's under `File` -> `Settings`.

We refer to these global settings as `Preferences` in the rest of the document.

## Build TruffleRuby

First, make sure the project is already built from the command line:

```bash
jt build
```

## Install Eclipse Code Formatter Plugin

Installing the Eclipse Code Formatter plugin is recommended to have the Java
source code always formatted to match the project's guidelines.

1.  Install the `Eclipse Code Formatter` plugin with `Preferences` -> `Plugins`.

2.  The plugin will be fully configured in next step where the project files are
    generated with `jt idea`.
    
To format the source code use the `Reformat Code` IntelliJ shortcut.
That's `⌥⌘L` on macOS and `Ctrl+Alt+L` on Linux.
If imports are not being optimized as well, use IntelliJ action `Reformat File...`
(`⌥⇧⌘L` on macOS and `Ctrl+Alt+Shift+L` on Linux)
and make sure the checkbox `Optimize imports` is checked.

You can also format the whole codebase on the command line with `jt format`.

## Global Format Settings

These settings should be set to avoid unwanted formatting changes to the codebase.

In `Preferences` -> `Editor` -> `Code Style` -> `Java`:
- In `Imports` tab set "Class count to use import '*'" to a large value to disable import *, e.g. `999`
- In `Wrapping and Braces` tab -> `Field Annotations` select the option `Do not wrap after a single annotation`.

## Install Ruby and Python Plugins

To be able to see and edit properly all the files in the TruffleRuby repository
you need to install the plugins for Ruby and Python in `Preferences` -> `Plugins`.

Make sure you have configured Ruby and Python SDKs. When the project files are
generated it will try to use existing Python 2.7 SDK and a Ruby SDK which has
`truffleruby-jvm` in its name. If `rbenv` is used the `truffleruby-jvm` build
should be linked automatically and the `rbenv: truffleruby-jvm` Ruby SDK
should be created automatically when the IDE starts.

## Generate the Project Files

```bash
jt idea
```

If you want to include other GraalVM projects besides TruffleRuby and its dependencies, you can do so
by dynamically importing (`--dy`) other suites. For example, to have Native Image and Tools in the IDE:

```bash
jt idea --dy /substratevm,/tools
```

## Import the Project

From the IntelliJ launcher, select `Open` and navigate to this repository root.
There should now be the following in your workspace:

*   `com`, `org` – roots of Java packages TruffleRuby depend on
*   `mx` – the mx tool
*   `truffleruby` – Root directory which contains all TruffleRuby files 
    including the Java and Ruby source files. Syntax highlighting and project 
    navigation should work for both Java and Ruby.

Go to `File` -> `Project Structure` -> `Platform Settings` -> `SDKs`
and add the JVMCI OpenJDK shown by `$ jt install jvmci` with the `+` button -> `Add JDK...`.

## Configuring Checkstyle Plugin

Checkstyle style checks for Java code can be imported into IntelliJ via the
[CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea)
plugin.

This is optional, and you can choose to just use `jt checkstyle` and
`tool/cleanup-imports.rb` instead.

### Installation
1. Select `Preferences`, then `Plugins`.
2. Select `Marketplace` and search for `Checkstyle`
3. Select `Checkstyle-IDEA`, then `Install`.

### Configuration
1. Select `Preferences`, `Tools`, then `Checkstyle`.
2. Select `+` to add a local Checkstyle Configuration.
3. Enter a description, e.g. `TruffleRuby Checkstyle`
4. Enter the location of the configuration file, e.g. `src/main/.checkstyle_checks.xml`
5. Complete the import, then check the `Active` checkbox next to the new configuration, then select `OK`.

### Scan Usage
1. Select `View` -> `Tool Windows` -> `Checkstyle`
2. The Checkstyle tools window has options to check the current file, project, module, changelist, or only modified files.
3. The style issues will be highlighted in the tool window, on the right side of the editor or inline in the source.

### Reformat Code Usage
Using the `Code` -> `Reformat Code` tool using IntelliJ's default settings may resolve the majority of Checkstyle
errors. Optionally, you can import Checkstyle styles into your code formatting as follows:

1. Select `Preferences` -> `Editor` -> `Code Style`.
2. Using the gear icon, next to the `Scheme`, select `Import Scheme` -> `Checkstyle Configuration`.
3. Select the `src/main/.checkstyle_checks.xml` file to import Checkstyle into the scheme.
4. Use `Reformat Code` tool as usual with the new Checkstyle settings imported.

## Wrap to Column Plugin

The `Wrap to Column` plugin is useful to conveniently wrap to a given max number
of characters per line, for instance in Markdown files. Install it from
`Preferences` -> `Plugins`. You then need to configure the width to `80` in
`Preferences` -> `Tools` -> `Wrap to Column` -> `Override right margin`.
