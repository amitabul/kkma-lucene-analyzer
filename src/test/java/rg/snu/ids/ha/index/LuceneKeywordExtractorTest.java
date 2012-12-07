package rg.snu.ids.ha.index;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.snu.ids.ha.index.LuceneKeywordExtractor;

public class LuceneKeywordExtractorTest {

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void test() {
    LuceneKeywordExtractor extractor = new LuceneKeywordExtractor();
    extractor.extractTokens("무궁화 무궁화꽃이 11m/s 피었습니다", true);
    fail("Not yet implemented");
  }

}
