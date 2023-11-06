package org.homio.addon.firmata.platform;

/**
 * Interface for an in-memory storage of text file contents. This is
 * intended to allow a GUI to keep modified text in memory, and allow
 * SketchFile to check for changes when needed.
 */
public interface TextStorage {
  /**
   * Get the current text
   */
  String getText();

  /**
   * Is the text modified externally, after the last call to
   * clearModified() or setText()?
   */
  boolean isModified();

  /**
   * Clear the isModified() result value
   */
  void clearModified();
}
