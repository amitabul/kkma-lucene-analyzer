package org.snu.ids.ha.index;

import java.util.ArrayList;
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
  public KeywordList extractTokens(String string, boolean onlyNoun) {
    List<Keyword> keywords = new ArrayList<Keyword>();
    EnglishStemmer engStemmer = new EnglishStemmer();
    SortedSet<TokenInfo> tokenInfos = new TreeSet<TokenInfo>();

    try {
      List<MExpression> meList = leaveJustBest(postProcess(analyze(string)));

      Morpheme mp = null;
      MCandidate mc = null;
      MExpression me = null;
      Keyword keyword = null;
      List<Morpheme> mpList = new ArrayList<Morpheme>();
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
          for (int j = 0; j < jSize; j++)
            mpList.add(mc.get(j));
        }

      }

      // 복합 UOM 확인
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
            mp = mpList.get(startIdx);
            mp.setString(tempName);
            mp.setCharSet(CharSetType.COMBINED);
            mp.setTag(POSTag.NNM);
          }
          // 다수의 토큰으로 이루어진 화학식 확인
          else if (ChemFormulaDic.contains(tempName)) {
            for (; startIdx < endIdx; endIdx--) {
              mpList.remove(startIdx + 1);
            }
            mp = mpList.get(startIdx);
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
              mp = mpList.get(startIdx);
              mp.setString(tempName);
              mp.setCharSet(CharSetType.COMBINED);
              mp.setTag(POSTag.NNG);
              mp.setComposed(true);
            }
          }
        }
      }
      
      // 키워드 추출
      for (int i = 0, size = mpList.size(); i < size; i++) {
        mp = mpList.get(i);
        mp.setString(mp.getString().toLowerCase());

        // stemming 및 키워드 추출
        if ((!onlyNoun || mp.isTagOf(POSTag.N)) && !JunkWordDic.contains(mp.getString())) {

          // do stemming english word
          if (mp.isTagOf(POSTag.UN) && mp.getCharSet() == CharSetType.ENGLISH) {
            keyword = new Keyword(mp);
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
              keyword = new Keyword(mp);
              keyword.setString(temp);
              keyword.setTag(POSTag.NNG);
              keywords.add(keyword);
            }
            // 일반 용언 처리
            else {
              keyword = new Keyword(mp);
              keywords.add(keyword);
            }
          }
          // 이외 적합한 경우에 추가
          else if (!mp.isTagOf(POSTag.NP) || true) {
            keyword = new Keyword(mp);
            keywords.add(keyword);
          }
        }
      }

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
              keyword = new Keyword(mp0);
              keyword.setComposed(true);
              keyword.setString(mp0.getString() + mp1.getString() + mp2.getString()
                  + mp3.getString());
              keywords.add(keyword);
              step++;
            } else {
              keyword = new Keyword(mp0);
              keyword.setComposed(true);
              keyword.setString(mp0.getString() + mp1.getString() + mp2.getString());
              keywords.add(keyword);
            }
            step++;
          } else {
            keyword = new Keyword(mp0);
            keyword.setComposed(true);
            keyword.setString(mp0.getString() + mp1.getString());
            keywords.add(keyword);
          }
          step++;
        }
        i += step;
      }

      // 조건 확인으로 불용어 제거
      for (int i = 0; i < keywords.size(); i++) {
        keyword = keywords.get(i);

        // 접두사, 접미사 제거, 보조 용언 제거, 불용어 제거
        if (keyword.isTagOf(POSTag.XP | POSTag.XS | POSTag.VX)
            || JunkWordDic.contains(mp.getString())) {
          keywords.remove(i);
          i--;
        }
      }

      // 복합 명사의 분석 결과를 읽어온다.
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
      keywords.addAll(cnKeywordList);

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
        tokenInfos.add(new TokenInfo(
            k.getString(), (k.isComposed() ? 0 : 1), offsets));
      }
      
      // 어절 추가
      /*
      Morpheme mp = null;
      MCandidate mc = null;
      MExpression me = null;
      Keyword keyword = null;
      List<Morpheme> mpList = new ArrayList<Morpheme>();
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
          for (int j = 0; j < jSize; j++)
            mpList.add(mc.get(j));
        }
  
      }
      */
      for (MExpression mexp: meList) {
        String eojeol = mexp.getExp();
        Morpheme firstMorpheme = mexp.get(0).get(0);
        int startOffset = firstMorpheme.getIndex();
        Offsets offsets =
            new Offsets(startOffset, startOffset + eojeol.length());
        if (firstMorpheme.getTag().substring(0,0) == "N") { 
          tokenInfos.add(new TokenInfo(eojeol, 0, offsets));
        } else {
          tokenInfos.add(new TokenInfo(eojeol, 1, offsets));
        }
      }
      
      System.out.println(tokenInfos);
    
    } catch (Exception e) {
      System.err.println(string);
      e.printStackTrace();
    }
    
    System.out.println(keywords);
   
    

    return new KeywordList(keywords);
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
          "TermInfo(" + term + ":" + posIncr + ":" +
          offsets.start + ":" + offsets.end + ")");
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
