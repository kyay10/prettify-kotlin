package io.github.kyay10.prettifykotlin

import arrow.optics.Lens
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.table.TableModelEditor.EditableColumnInfo
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

fun Document?.prettyFoldingDescriptor(
  node: PsiElement,
  placeholder: String,
  range: TextRange = node.textRange,
  dependencies: Set<Any?> = setOf(),
) = FoldingDescriptor(node.node, range, null, dependencies, true, placeholder, true).apply {
  setCanBeRemovedWhenCollapsed(true)
}.takeIf { (range shl node.textRange.startOffset).substring(node.text) != placeholder }?.also {
  this ?: return@also
  for (highlighter in EditorFactory.getInstance().getEditors(this, node.project)
    .flatMap { it.markupModel.allHighlighters.toList() } +
    DocumentMarkupModel.forDocument(this, node.project, false).allHighlighters) {
    if (highlighter.textRange.intersects(range) && highlighter is RangeHighlighterEx) {
      highlighter.isVisibleIfFolded = true
    }
  }
}

fun KaAnnotation.findConstantArgument(name: Name): Any? = findConstantArgument(name) { null }

inline fun <reified T> KaAnnotation.findConstantArgument(name: Name, default: () -> T): T {
  val constValue = (arguments.find { it.name == name }?.expression as? KaAnnotationValue.ConstantValue)?.value
  val value = constValue?.value
  if (constValue == null || value !is T) return default()
  return value
}

private val singleCharacterRange = TextRange(0, 1)

infix fun TextRange.shr(delta: Int) = shiftRight(delta)
infix fun TextRange.shl(delta: Int) = shiftLeft(delta)

val PsiElement.foldForSingleSpaceBefore: FoldingDescriptor?
  get() = prevSibling.takeIf { it.text.endsWith(" ") }?.let {
    null.prettyFoldingDescriptor(it, "", range = singleCharacterRange shr it.textRange.endOffset - 1)
  }

val PsiElement.foldForSingleSpaceAfter: FoldingDescriptor?
  get() = nextSibling.takeIf { it.text.startsWith(" ") }?.let {
    null.prettyFoldingDescriptor(it, "", range = singleCharacterRange shr it.textRange.startOffset)
  }

val allKeywords = buildList {
  addAll(KtTokens.KEYWORDS.types)
  addAll(KtTokens.SOFT_KEYWORDS.types)
}.filterIsInstance<KtSingleValueToken>().associateBy { it.value }.toSortedMap()

fun Project.updateFoldings() {
  EditorFactory.getInstance().allEditors.forEach { editor ->
    if (editor.virtualFile?.extension == "kt" && editor.project == this)
      CodeFoldingManager.getInstance(this).scheduleAsyncFoldingUpdate(editor)
  }
}

abstract class OpticsStateComponent<T : Any>(state: T) : SerializablePersistentStateComponent<T>(state) {
  fun <A> Lens<T, A>.update(value: A) {
    updateState { set(it, value) }
  }
}

operator fun <T : Any, A> Lens<T, A>.getValue(thisRef: OpticsStateComponent<T>, p: KProperty<*>): A =
  get(thisRef.state)

operator fun <T : Any, A> Lens<T, A>.setValue(thisRef: OpticsStateComponent<T>, p: KProperty<*>, value: A) =
  with(thisRef) { update(value) }

inline fun <T : Any, reified A> columnInfo(name: String, crossinline value: T.() -> A) =
  object : ColumnInfo<T, A>(name) {
    override fun getColumnClass() = A::class.java
    override fun valueOf(item: T) = item.value()
  }

inline fun <T : Any, reified A> columnInfo(name: String, prop: KMutableProperty1<T, A>) =
  object : EditableColumnInfo<T, A>(name) {
    override fun getColumnClass() = A::class.java
    override fun valueOf(item: T) = prop(item)
    override fun setValue(item: T, value: A) = prop.set(item, value)
  }