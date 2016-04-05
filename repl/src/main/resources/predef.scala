import org.apache.log4j.{Level, Logger}

import scraper.Context._
import scraper.config.Settings
import scraper.expressions._
import scraper.expressions.dsl._
import scraper.expressions.functions._
import scraper.local.LocalContext
import scraper.types._

implicit val context = new LocalContext(Settings.load("scraper.conf", "scraper-reference.conf"))

def setLogLevel(level: String) {
  Logger.getRootLogger.setLevel(Level.toLevel(level))
}

context range 100 select (
  when (((rand(42) * 10) cast IntType) % 10 < 2) {
    lit(null)
  } otherwise {
    'id
  } as 'id
) asTable 't
