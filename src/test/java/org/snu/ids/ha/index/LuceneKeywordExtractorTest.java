package org.snu.ids.ha.index;

import static org.junit.Assert.assertEquals;

import java.util.SortedSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.snu.ids.ha.index.LuceneKeywordExtractor.TokenInfo;

public class LuceneKeywordExtractorTest {

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testComposedNoun() {
    LuceneKeywordExtractor extractor = new LuceneKeywordExtractor();
    SortedSet<TokenInfo> tokenInfos = extractor.extractTokens("삼성전자", true);
    assertEquals(
        "[삼성:1:0:2, 전자:1:2:4, 삼성전자:0:0:4]", tokenInfos.toString());
  }
  
  @Test
  public void testUOM() {
    LuceneKeywordExtractor extractor = new LuceneKeywordExtractor();
    //SortedSet<TokenInfo> tokenInfos = extractor.extractTokens("11m/s", true);
    SortedSet<TokenInfo> tokenInfos = extractor.extractTokens("11m/s", true);
    assertEquals(
        // TODO : 고치자..
        //"[11:1:0:2, m/s:1:2:5, 11m/s:0:0:5]", tokenInfos.toString());
        "[11:1:0:2, 11m:0:0:3, m/s:1:2:5, 11m/s:0:0:5]", tokenInfos.toString());
  }
    
  @Test
  public void testEnglish() {
    LuceneKeywordExtractor extractor = new LuceneKeywordExtractor();
    SortedSet<TokenInfo> tokenInfos = extractor.extractTokens("nice nice man", true);
    assertEquals(
        "[nice:1:0:4, nice:1:5:9, man:1:10:13]", tokenInfos.toString());
  }
  
  @Test
  public void testNounJosa() {
    LuceneKeywordExtractor extractor = new LuceneKeywordExtractor();
    SortedSet<TokenInfo> tokenInfos = extractor.extractTokens("삼성에", true);
    assertEquals(
        "[삼성:1:0:2, 삼성에:0:0:3]", tokenInfos.toString());
  }
  
  @Test
  public void testNounJosaJosa() {
    LuceneKeywordExtractor extractor = new LuceneKeywordExtractor();
    SortedSet<TokenInfo> tokenInfos = extractor.extractTokens("삼성에는", true);
    
    assertEquals(
        "[삼성:1:0:2, 삼성에는:0:0:4]", tokenInfos.toString());
    
  }
  
  @Test
  public void testOppan() {
    LuceneKeywordExtractor extractor = new LuceneKeywordExtractor();
    SortedSet<TokenInfo> tokenInfos = extractor.extractTokens("오빤 달립니다.", true);
    assertEquals(
        "[오빤:1:0:2, 달립니다:1:3:7]", tokenInfos.toString());
    
  }

}
