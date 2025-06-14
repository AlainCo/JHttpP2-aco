package fr.alainco.jhttpp2.core.internal;
/* Written and copyright 2001-2011 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 * More Information and documentation: HTTP://jhttp2.sourceforge.net/
 */
 
/**
 * Title:        jHTTPp2: Java HTTP Filter Proxy
 * Copyright:    Copyright (c) 2001-2011 Benjamin Kohl
 * @author Benjamin Kohl
 * @version 0.2.8
 */

public class Jhttpp2URLMatch implements java.io.Serializable
{
  String match;
  String desc;
  boolean cookies_enabled;
  int actionindex;

  public Jhttpp2URLMatch(String match,boolean cookies_enabled,int actionindex,String description)
  {
    this.match=match;
    this.cookies_enabled=cookies_enabled;
    this.actionindex=actionindex;
    this.desc=description;
  }
  public String getMatch()
  {
    return match;
  }
  public boolean getCookiesEnabled()
  {
    return cookies_enabled;
  }
  public int getActionIndex()
  {
    return actionindex;
  }
  public String getDescription()
  {
    return desc;
  }
  public String toString()
  {
    return "\"" + match + "\" " + desc;
  }
}