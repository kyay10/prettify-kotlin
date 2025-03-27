package io.github.kyay10.prettifykotlin

annotation class Pretty(val name: String, val infixOnly: Boolean = false)
annotation class Prefix(val prefix: String = "", val suffix: String = "")
annotation class Postfix(val suffix: String = "")
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class AutoLambda(val prefix: String = "", val arrow: String = "->", val suffix: String = "")