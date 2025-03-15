# prettify-kotlin

![Build](https://github.com/kyay10/prettify-kotlin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/23188-prettify-kotlin.svg)](https://plugins.jetbrains.com/plugin/23188-prettify-kotlin)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/23188-prettify-kotlin.svg)](https://plugins.jetbrains.com/plugin/23188-prettify-kotlin)

<!-- Plugin description -->
Prettify Kotlin is a plugin that uses Idea's code folding to display backticked function calls without backticks. This
is useful for Kotlin DSLs that define custom "operators" (e.g. ``` `<-` ```) or use illegal characters (e.g.
sigma ``` `Σ` ```).

It also supports providing a "pretty" name for a function with the `@Pretty`
annotation. (`"io.github.kyay10:prettify-kotlin-annotation:1.0"`)

It's recommended that you turn off the foreground and background colors for folded text in the IDE
<kbd>Settings/Preferences</kbd> > <kbd>Editor</kbd> > <kbd>Color Scheme</kbd> > <kbd>General</kbd> > <kbd>
Text</kbd> > <kbd>Folded text</kbd>
<!-- Plugin description end -->

This plugin is inspired
by [zjhmale/intellij-closure-pretty-symbol](https://github.com/zjhmale/intellij-clojure-pretty-symbol)

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Prettify
  Kotlin"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/kyay10/prettify-kotlin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template