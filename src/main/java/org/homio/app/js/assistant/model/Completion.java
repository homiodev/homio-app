package org.homio.app.js.assistant.model;

import lombok.Getter;

@Getter
public class Completion implements Comparable<Completion> {

  /**
   * The label of this completion item. By default this is also the text that is inserted when selecting this completion.
   */
  String label;
  /**
   * The kind of this completion item. Based on the kind an icon is chosen by the editor.
   */
  int kind;
  /**
   * A modifier to the `kind` which affect how the item is rendered, e.g. Deprecated is rendered with a strikeout
   */
  Object tags;//?:ReadonlyArray<CompletionItemTag>;
  /**
   * A human-readable string with additional information about this item, like type or symbol information.
   */
  String detail;
  /**
   * A human-readable string that represents a doc-comment.
   */
  String documentation;//?:string |IMarkdownString;
  /**
   * A string that should be used when comparing this item with other items. When `falsy` the [label](#CompletionItem.label) is used.
   */
  String sortText = "falsy";
  /**
   * A string that should be used when filtering a set of completion items. When `falsy` the [label](#CompletionItem.label) is used.
   */
  String filterText;
  /**
   * Select this item when showing. *Note* that only one completion item can be selected and that the editor decides which item that is. The rule is that the
   * *key* item of those that match best is selected.
   */
  boolean preselect;
  /**
   * A string or snippet that should be inserted in a document when selecting this completion. is used.
   */
  String insertText;
  /**
   * Addition rules (as bitmask) that should be applied when inserting this completion.
   */
  Object insertTextRules;//?:CompletionItemInsertTextRule;
  /**
   * A range of text that should be replaced by this completion item.
   * <p>
   * Defaults to a range from the start of the [current word](#TextDocument.getWordRangeAtPosition) to the current position.
   * <p>
   * *Note:* The range must be a [single line](#Range.isSingleLine) and it must [contain](#Range.contains) the position at which completion has been
   * [requested](#CompletionItemProvider .provideCompletionItems).
   */
  IRange range;
  /**
   * An optional set of characters that when pressed while this completion is active will accept it key and then type that character. *Note* that all commit
   * characters should have `length=1` and that superfluous characters will be ignored.
   */
  String[] commitCharacters;
  /**
   * An optional array of additional text edits that are applied when
   * selecting this completion. Edits must not overlap with the main edit
   * nor with themselves.
   */
  // additionalTextEdits?:editor.ISingleEditOperation[];

  /**
   * A command that should be run upon acceptance of this item.
   */
  public Completion(String word, String retValue, Class type, String help, CompletionItemKind kind) {
    this.label = word;
    this.kind = kind.ordinal();
    this.documentation = help;
    this.insertText = word;
    this.detail = retValue;
  }

  @Override
  public int compareTo(Completion completion) {
    return this.label.compareTo(completion.label);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Completion that = (Completion) o;

    return label.equals(that.label);
  }

  @Override
  public int hashCode() {
    return label.hashCode();
  }
}

class IRange {

  /**
   * Line number on which the range starts (starts at 1).
   */
  int startLineNumber;
  /**
   * Column on which the range starts in line `startLineNumber` (starts at 1).
   */
  int startColumn;
  /**
   * Line number on which the range ends.
   */
  int endLineNumber;
  /**
   * Column on which the range ends in line `endLineNumber`.
   */
  int endColumn;
}
