package org.homio.addon.camera.onvif.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * class has static functions that help the IpCamera binding not need as many external libs.
 */
public class Helper {

  /**
   * Used to grab values out of JSON or other quote encapsulated structures without needing an external lib. String may be terminated by ," or }.
   *
   * @author Matthew Skinner - Initial contribution
   */
  public static String searchString(String rawString, String searchedString) {
    String result = "";
    int index = 0;
    index = rawString.indexOf(searchedString);
    if (index != -1) // -1 means "not found"
    {
      result = rawString.substring(index + searchedString.length(), rawString.length());
      index = result.indexOf(',');
      if (index == -1) {
        index = result.indexOf('"');
        if (index == -1) {
          index = result.indexOf('}');
          if (index == -1) {
            return result;
          } else {
            return result.substring(0, index);
          }
        } else {
          return result.substring(0, index);
        }
      } else {
        result = result.substring(0, index);
        index = result.indexOf('"');
        if (index == -1) {
          return result;
        } else {
          return result.substring(0, index);
        }
      }
    }
    return "";
  }

  public static String fetchXML(String message, String sectionHeading, String key) {
    String result = "";
    int sectionHeaderBeginning = 0;
    if (!sectionHeading.isEmpty()) {// looking for a sectionHeading
      sectionHeaderBeginning = message.indexOf(sectionHeading);
    }
    if (sectionHeaderBeginning == -1) {
      return "";
    }
    int startIndex = message.indexOf(key, sectionHeaderBeginning + sectionHeading.length());
    if (startIndex == -1) {
      return "";
    }
    int endIndex = message.indexOf("<", startIndex + key.length());
    if (endIndex > startIndex) {
      result = message.substring(startIndex + key.length(), endIndex);
    }
    // remove any quotes and anything after the quote.
    sectionHeaderBeginning = result.indexOf("\"");
    if (sectionHeaderBeginning > 0) {
      result = result.substring(0, sectionHeaderBeginning);
    }
    // remove any ">" and anything after it.
    sectionHeaderBeginning = result.indexOf(">");
    if (sectionHeaderBeginning > 0) {
      result = result.substring(0, sectionHeaderBeginning);
    }
    return result;
  }

  /**
   * Is used to replace spaces with %20 in Strings meant for URL queries.
   */
  public static String encodeSpecialChars(String text) {
    String processed = text;
    try {
      processed = URLEncoder.encode(text, "UTF-8").replace("+", "%20");
    } catch (UnsupportedEncodingException e) {
    }
    return processed;
  }
}
