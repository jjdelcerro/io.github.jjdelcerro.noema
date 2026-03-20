package io.github.jjdelcerro.noema.lib.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.ocpsoft.prettytime.PrettyTime;

/**
 *
 * @author jjdelcerro
 */
public class DateUtils {

    public static Timestamp timestampNow() {
      return Timestamp.valueOf(LocalDateTime.now());
    }
    
    public static String now() {
      return toString(LocalDateTime.now());
    }
    
    public static String toString(LocalDateTime d) {
      if( d == null ) {
        return "";
      }
//      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", Locale.of("es"));
//      return LocalDateTime.now().format(formatter);
      return Timestamp.valueOf(d).toString();
    }
    
    public static LocalDateTime toLocalDateTime(String s) {
      if( StringUtils.isBlank(s) ) {
        return null;
      }
      return Timestamp.valueOf(s).toLocalDateTime();
    }

    public static String timeAgo(LocalDateTime d) {
      PrettyTime pt = new PrettyTime(Locale.of("es"));
      String timeAgo = pt.format(d);
      return timeAgo;
    }

}
