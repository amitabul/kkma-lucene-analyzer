package org.snu.ids.ha.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.snu.ids.ha.constants.POSTag;
import org.snu.ids.ha.index.Keyword;
import org.snu.ids.ha.index.KeywordExtractor;
import org.snu.ids.ha.index.KeywordList;
import org.snu.ids.ha.ma.CharSetType;
import org.snu.ids.ha.ma.MCandidate;
import org.snu.ids.ha.ma.MExpression;
import org.snu.ids.ha.ma.Morpheme;
import org.tartarus.snowball.EnglishStemmer;

public class LuceneKeywordExtractor extends KeywordExtractor {
  /**
   * extract index word from the given string
   * 
   * @author bibreen <bibreen@gmail.com>
   * @author amitabul <mousegood@gmail.com>
   * @return the keyword list
   */
  
  
  public SortedSet<TokenInfo> extractTokens(String string, boolean onlyNoun) {
    SortedSet<TokenInfo> tokenInfos = new TreeSet<TokenInfo>();

    List<Keyword> keywords = new ArrayList<Keyword>();
    try {
      List<MExpression> meList = leaveJustBest(postProcess(analyze(string)));

      List<Morpheme> mpList = generateMpList(meList);

      // 복합 UOM 생성. 원형태소는 삭제하고 복합 UOM를 새로 생성하여 삽입.
      findUomAndRegenerateMpList(mpList);
      
      // 키워드 추출(mpList -> Keyword)
      keywords.addAll(extractKeywords(onlyNoun, mpList));

      // 명사가 이어 나오면 붙인다.
      keywords.addAll(extractComposedNoun(mpList));
      
      // TODO 어절을 만들어 keywords 에 넣는다.
      keywords.addAll(generateEojeol(mpList));

      removeMorepheme(keywords, POSTag.XP | POSTag.XS | POSTag.VX);
      removeStopword(keywords);
      

        // 복합명사는 분해하여 keyword 등록.
      keywords.addAll(decompose(keywords));

      // index순서대로 정렬한다.
      Collections.sort(keywords, new Comparator<Keyword>() {
        public int compare(Keyword o1, Keyword o2) {
          if (o1.getIndex() == o2.getIndex()) {
            return o1.getString().length() - o2.getString().length();
          }
          return o1.getIndex() - o2.getIndex();
        }
      });
      
      // 형태소 추가
      for (Keyword k: keywords) {
        Offsets offsets =
            new Offsets(k.getIndex(), k.getIndex() + k.getString().length());
        // 원어절 삽입
        if (k.getVocTag().compareTo("Origin") == 0) {
          tokenInfos.add(new TokenInfo(
              k.getString(), 0, offsets));
        } else {
          tokenInfos.add(new TokenInfo(
              k.getString(), (k.isComposed() ? 0 : 1), offsets));
        }
      }
      
//      // 어절 추가
//      for (MExpression mexp: meList) {
//        String eojeol = mexp.getExp();
//        Morpheme firstMorpheme = mexp.get(0).get(0);
//        int startOffset = firstMorpheme.getIndex();
//        Offsets offsets =
//            new Offsets(startOffset, startOffset + eojeol.length());
//        if (firstMorpheme.isTagOf(POSTag.N)) { 
//          tokenInfos.add(new TokenInfo(eojeol, 0, offsets));
//        } else {
//          tokenInfos.add(new TokenInfo(eojeol, 1, offsets));
//        }
//      }
//      
    
    } catch (Exception e) {
      System.err.println(string);
      e.printStackTrace();
    }
    
    return tokenInfos;
  }

  private List<Keyword> generateEojeol(List<Morpheme> mpList) {
    List<Keyword> keywords = new ArrayList<Keyword>();
    Morpheme prePreMorpheme = null;
    Morpheme preMorpheme = null;
    for (int i = (mpList.size()-1); i >= 0; i--) {
      Morpheme morpheme = mpList.get(i);
      if (morpheme.isTagOf(POSTag.N)) {
        if (preMorpheme != null && preMorpheme.isTagOf(POSTag.J)) {
          // 명사 + 조사 + 조사
          if (prePreMorpheme != null && preMorpheme.isTagOf(POSTag.J)) {
            Keyword keyword = new Keyword(prePreMorpheme);
            keyword.setVocTag("Origin"); 
            keyword.setString(
                morpheme.getString() 
                + preMorpheme.getString() 
                + prePreMorpheme.getString());
            keyword.setComposed(false);
            keyword.setIndex(morpheme.getIndex());
            keywords.add(keyword);
          // 명사 + 조사
          } else {
            Keyword keyword = new Keyword(preMorpheme);
            keyword.setVocTag("Origin"); 
            keyword.setString(morpheme.getString() + preMorpheme.getString());
            keyword.setComposed(false);
            keyword.setIndex(morpheme.getIndex());
            keywords.add(keyword);
          }
        }
        
      }
      prePreMorpheme = preMorpheme;
      preMorpheme = morpheme;
    }
    
    // 용언  + 접미사? ..
    
    return keywords;
  }

  private List<Keyword> decompose(List<Keyword> keywords) {
    List<Keyword> cnKeywordList = new ArrayList<Keyword>();
    String[] cnKeywords = null;
    for (int i = 0, size = keywords.size(); i < size; i++) {
      Keyword k = keywords.get(i);
      if (k.isComposed() && (cnKeywords = dic.getCompNoun(k.getString())) != null) {
        int addIdx = 0;
        for (int j = 0, len = cnKeywords.length; j < len; j++) {
          if (JunkWordDic.contains(cnKeywords[j]))
            continue;
          Keyword newKeyword = new Keyword(k);
          newKeyword.setVocTag("E");
          newKeyword.setString(cnKeywords[j]);
          newKeyword.setComposed(false);
          newKeyword.setIndex(k.getIndex() + addIdx);
          addIdx += newKeyword.getString().length();
          cnKeywordList.add(newKeyword);
        }
      }
    }
    return cnKeywordList;
  }

  private void removeStopword(List<Keyword> keywords) {
    // TODO Auto-generated method stub
    // 조건 확인으로 불용어 제거
    for (int i = 0; i < keywords.size(); i++) {
      Keyword keyword = keywords.get(i);

      // 접두사, 접미사 제거, 보조 용언 제거, 불용어 제거
      if (JunkWordDic.contains(keyword.getString())) {
        keywords.remove(i);
        i--;
      }
    }

    
  }

  private void removeMorepheme(List<Keyword> keywords, long posTags) {
    for (int i = 0; i < keywords.size(); i++) {
      Keyword keyword = keywords.get(i);

      // 접두사, 접미사 제거, 보조 용언 제거, 불용어 제거
      if (keyword.isTagOf(posTags)) {
        keywords.remove(i);
        i--;
      }
    }
    
  }

  private List<Keyword> extractComposedNoun(List<Morpheme> mpList) {
    List<Keyword> keywords = new ArrayList<Keyword>(); 
    Morpheme mp0 = null, mp1 = null, mp2 = null, mp3 = null;
    for (int i = 0, size = mpList.size(), step = 0; i < size; i++) {
      mp0 = mpList.get(i);
      step = 0;

      // 복합 명사 추출 --------------
      // 두글자 복합 명사 추출
      if (i + 1 < size && mp0.isTagOf(POSTag.NN) && (mp1 = mpList.get(i + 1)).isTagOf(POSTag.NN)
          && mp0.getIndex() + mp0.getString().length() == mp1.getIndex()) {
        // 세글자 복합 명사 추출
        if (i + 2 < size && (mp2 = mpList.get(i + 2)).isTagOf(POSTag.NN)
            && mp1.getIndex() + mp1.getString().length() == mp2.getIndex()) {
          // 네글자 복합명사 추출
          if (i + 3 < size && (mp3 = mpList.get(i + 3)).isTagOf(POSTag.NN)
              && mp2.getIndex() + mp2.getString().length() == mp3.getIndex()) {
            Keyword keyword = new Keyword(mp0);
            keyword.setComposed(true);
            keyword.setString(mp0.getString() + mp1.getString() + mp2.getString()
                + mp3.getString());
            keywords.add(keyword);
            step++;
          } else {
            Keyword keyword = new Keyword(mp0);
            keyword.setComposed(true);
            keyword.setString(mp0.getString() + mp1.getString() + mp2.getString());
            keywords.add(keyword);
          }
          step++;
        } else {
          Keyword keyword = new Keyword(mp0);
          keyword.setComposed(true);
          keyword.setString(mp0.getString() + mp1.getString());
          keywords.add(keyword);
        }
        step++;
      }
      i += step;
    }
    return keywords;
  }

  private void findUomAndRegenerateMpList(List<Morpheme> mpList) {
    for (int endIdx = mpList.size() - 1; endIdx > 0; endIdx--) {
      for (int startIdx = Math.max(endIdx - MAX_UOM_SIZE, 0); startIdx < endIdx; startIdx++) {
        String tempName = "";
        for (int i = startIdx; i <= endIdx; i++) {
          tempName += mpList.get(i).getString();
        }

        // 다수의 토큰으로 이루어진 UOM 확인
        if (UOMDic.contains(tempName)) {
          for (; startIdx < endIdx; endIdx--) {
            mpList.remove(startIdx + 1);
          }
          Morpheme mp = mpList.get(startIdx);
          mp.setString(tempName);
          mp.setCharSet(CharSetType.COMBINED);
          mp.setTag(POSTag.NNM);
        }
        // 다수의 토큰으로 이루어진 화학식 확인
        else if (ChemFormulaDic.contains(tempName)) {
          for (; startIdx < endIdx; endIdx--) {
            mpList.remove(startIdx + 1);
          }
          Morpheme mp = mpList.get(startIdx);
          mp.setString(tempName);
          mp.setCharSet(CharSetType.COMBINED);
          mp.setTag(POSTag.UN);
        }
        // 다수의 토큰으로 이루어진 명사 확인 ((주), Web2.0)류의 키워드
        else if (CompNounDic.contains(tempName)) {
          for (; startIdx < endIdx; endIdx--) {
            mpList.remove(startIdx + 1);
          }
          if (!JunkWordDic.contains(tempName)) {
            Morpheme mp = mpList.get(startIdx);
            mp.setString(tempName);
            mp.setCharSet(CharSetType.COMBINED);
            mp.setTag(POSTag.NNG);
            mp.setComposed(true);
          }
        }
      }
    }
  }

  private List<Morpheme> generateMpList(List<MExpression> meList) {
    List<Morpheme> mpList = new ArrayList<Morpheme>();
    Morpheme mp = null;
    MCandidate mc = null;
    MExpression me = null;
    for (int i = 0, size = meList == null ? 0 : meList.size(); i < size; i++) {
      me = meList.get(i);
      mc = me.get(0);

      int jSize = mc.size();
      if (jSize == 1) {
        mp = mc.get(0);
        mp.setString(me.getExp());
        mpList.add(mp);
      } else {
        // 분할되지 않은 리스트 형태로 형태소를 넣어준다.
        for (int j = 0; j < jSize; j++) {
          mpList.add(mc.get(j));
        }
      }
    }
    
    return mpList;
  }

  private List<Keyword> extractKeywords(boolean onlyNoun, List<Morpheme> mpList) {
    List<Keyword> keywords = new ArrayList<Keyword>();
    
    EnglishStemmer engStemmer = new EnglishStemmer();
    for (int i = 0, size = mpList.size(); i < size; i++) {
      Morpheme mp = mpList.get(i);
      mp.setString(mp.getString().toLowerCase());

      // stemming 및 키워드 추출
      if ((!onlyNoun || mp.isTagOf(POSTag.N)) && !JunkWordDic.contains(mp.getString())) {

        // do stemming english word
        if (mp.isTagOf(POSTag.UN) && (mp.getCharSet() == CharSetType.ENGLISH)) {
          Keyword keyword = new Keyword(mp);
          engStemmer.setCurrent(keyword.getString().toLowerCase());
          engStemmer.stem();
          keyword.setString(engStemmer.getCurrent());
          keywords.add(keyword);
        }
        // 사랑하 로 추출된 경우 명사 '사랑'을 색인어로 추출
        else if (mp.isTagOf(POSTag.V)) {
          String temp = mp.getString();
          int tempLen = temp.length();
          char ch = temp.charAt(tempLen - 1);
          if (tempLen > 2 && (ch == '하' || ch == '되')
              && VerbNounDic.contains(temp = temp.substring(0, tempLen - 1))) {
            Keyword keyword = new Keyword(mp);
            keyword.setString(temp);
            keyword.setTag(POSTag.NNG);
            keywords.add(keyword);
          }
          // 일반 용언 처리
          else {
            Keyword keyword = new Keyword(mp);
            keywords.add(keyword);
          }
        }
        // 이외 적합한 경우에 추가
        else if (!mp.isTagOf(POSTag.NP) || true) {
          Keyword keyword = new Keyword(mp);
          keywords.add(keyword);
        }
      }
    }
    return keywords;
  }
  
  public class TokenInfo implements Comparable<TokenInfo> {
    public static final long NO_INCREMENT_POSITION = -1L;
    private String term;
    private int posIncr;
    private Offsets offsets;
    
    public TokenInfo(String term, int posIncr, Offsets offsets) {
      this.term = term;
      this.posIncr = posIncr;
      this.offsets = offsets;
    }
    
    public String getTerm() {
      return term;
    }
    
    public int getPosIncr() {
      return posIncr;
    }
    
    public Offsets getOffsets() {
      return offsets;
    }

    @Override
    public int compareTo(TokenInfo t) {
      if (offsets.end == t.getOffsets().end) {
        // end가 같은 경우에는 보다 긴 term(start가 작은 term)이 상위이다.
        // start와 end가 동일한 term은 SortedSet에 들어갈 필요가 없다.
        return t.getOffsets().start - offsets.start;
      }
      return offsets.end - t.getOffsets().end;
    }
    
    @Override
    public String toString() {
      return new String(
          term + ":" + posIncr + ":" +
          offsets.start + ":" + offsets.end);
    }
  }
  
  class Offsets {
    public int start;
    public int end;
    
    public Offsets(int s, int e) {
      start = s;
      end = e;
    }
    
    @Override
    public String toString() {
      return new String("Offsets(" + start + ":" + end + ")");
    }
  }
}
