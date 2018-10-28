package com.monke.monkeybook.widget.page;

import android.text.TextUtils;

import com.monke.basemvplib.CharsetDetector;
import com.monke.monkeybook.base.observer.SimpleObserver;
import com.monke.monkeybook.bean.BookShelfBean;
import com.monke.monkeybook.bean.ChapterListBean;
import com.monke.monkeybook.help.BookshelfHelp;
import com.monke.monkeybook.help.FormatWebText;
import com.trello.rxlifecycle2.android.ActivityEvent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.MediaType;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.service.MediatypeService;

/**
 * 初步实现epub阅读
 *
 * 问题：1.加载速度慢
 *      2.无法加载图片
 */
public class EPUBPageLoader extends PageLoader {

    //编码类型
    private Charset mCharset;

    private Book mRawBook;

    private Disposable mChapterDisp = null;

    public EPUBPageLoader(PageView pageView, BookShelfBean collBook) {
        super(pageView, collBook);
        if (mCollBook.getChapterListSize() == 0) {
            setCurPageStatus(STATUS_PARING);
        }
    }

    private Book readBook(File file) {
        try {
            EpubReader epubReader = new EpubReader();
            MediaType[] lazyTypes = {
                    MediatypeService.CSS,
                    MediatypeService.GIF,
                    MediatypeService.JPG,
                    MediatypeService.PNG,
                    MediatypeService.MP3,
                    MediatypeService.MP4};

            return epubReader.readEpubLazy(file.getAbsolutePath(), "utf-8", Arrays.asList(lazyTypes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Charset getCharset(Book book) {
        try {
            Resource resource = book.getCoverPage();
            Document doc = Jsoup.parse(new String(resource.getData(), "utf-8"));
            Elements metaTags = doc.getElementsByTag("meta");
            for (Element metaTag : metaTags) {
                String charsetName = metaTag.attr("charset");
                if (!charsetName.isEmpty()) {
                    if (!TextUtils.isEmpty(charsetName)) {
                        return Charset.forName(charsetName);
                    }
                }
            }

            String charsetName = CharsetDetector.detectCharset(resource.getInputStream());
            return Charset.forName(charsetName);
        } catch (Exception e) {
            return Charset.forName("utf-8");
        }
    }

    private List<ChapterListBean> loadChapters() {
        List<ChapterListBean> chapterList = new ArrayList<>();
        List<SpineReference> spineReferences = mRawBook.getSpine().getSpineReferences();

        for (int i = 0, size = spineReferences.size(); i < size; i++) {
            Resource resource = spineReferences.get(i).getResource();
            String title = "";
            try {
                Document doc = Jsoup.parse(new String(resource.getData(), mCharset));
                Elements elements = doc.getElementsByTag("title");
                if (elements.size() > 0) {
                    title = elements.get(0).text();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            ChapterListBean bean = new ChapterListBean();
            bean.setDurChapterIndex(i);
            bean.setNoteUrl(mCollBook.getNoteUrl());
            bean.setDurChapterUrl(resource.getHref());
            if (i == 0) {
                bean.setDurChapterName("封面");
            } else {
                bean.setDurChapterName(title);
            }
            chapterList.add(bean);
        }

        return chapterList;
    }

    private String getChapterContent(ChapterListBean chapter) throws Exception {
        Resource resource = mRawBook.getSpine().getResource(chapter.getDurChapterIndex());
        StringBuilder content = new StringBuilder();
        Document doc = Jsoup.parse(new String(resource.getData(), mCharset));
        Elements elements = doc.getAllElements();
        for (Element element : elements) {
            List<TextNode> contentEs = element.textNodes();
            for (int i = 0; i < contentEs.size(); i++) {
                String text = contentEs.get(i).text().trim();
                text = FormatWebText.getContent(text);
                if (elements.size() > 1) {
                    if (text.length() > 0) {
                        if (content.length() > 0) {
                            content.append("\r\n");
                        }
                        content.append("\u3000\u3000").append(text);
                    }
                } else {
                    content.append(text);
                }
            }
        }
        return content.toString();
    }

    private Observable<BookShelfBean> checkChapterList(BookShelfBean collBook) {
        if (!collBook.getHasUpdate() && !collBook.realChapterListEmpty()) {
            return Observable.just(collBook);
        } else {
            return Observable.create((ObservableOnSubscribe<List<ChapterListBean>>) e -> {
                List<ChapterListBean> chapterList = loadChapters();
                if (!chapterList.isEmpty()) {
                    e.onNext(chapterList);
                } else {
                    e.onError(new IllegalAccessException("book sub-chapter failed!"));
                }
                e.onComplete();
            })
                    .flatMap(chapterList -> {
                        collBook.setChapterList(chapterList);
                        collBook.upChapterListSize();
                        return Observable.just(collBook);
                    })
                    .doOnNext(bookShelfBean -> {
                        // 存储章节到数据库
                        bookShelfBean.setHasUpdate(false);
                        bookShelfBean.setFinalRefreshData(System.currentTimeMillis());
                        if (BookshelfHelp.isInBookShelf(bookShelfBean.getNoteUrl())) {
                            BookshelfHelp.saveBookToShelf(bookShelfBean);
                        }
                    });
        }
    }

    @Override
    public void closeBook() {
        super.closeBook();
        if (mChapterDisp != null) {
            mChapterDisp.dispose();
            mChapterDisp = null;
        }
    }

    @Override
    public void refreshChapterList() {
        if (mCollBook == null) return;

        Observable.create((ObservableOnSubscribe<BookShelfBean>) e -> {
            File bookFile = new File(mCollBook.getNoteUrl());
            mRawBook = readBook(bookFile);

            if (mRawBook == null) {
                e.onError(new Exception("文件解析失败"));
                return;
            }

            String charsetName = mCollBook.getBookInfoBean().getCharset();
            if (TextUtils.isEmpty(charsetName)) {
                mCharset = getCharset(mRawBook);
                mCollBook.getBookInfoBean().setCharset(mCharset.name());
            } else {
                mCharset = Charset.forName(charsetName);
            }
            e.onNext(mCollBook);
            e.onComplete();
        }).subscribeOn(Schedulers.single())
                .flatMap(this::checkChapterList)
                .compose(mPageView.getActivity().bindUntilEvent(ActivityEvent.DESTROY))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<BookShelfBean>() {
                    @Override
                    public void onNext(BookShelfBean bookShelfBean) {
                        isChapterListPrepare = true;

                        // 加载并显示当前章节
                        skipToChapter(bookShelfBean.getDurChapter(), bookShelfBean.getDurChapterPage());

                        //提示目录加载完成
                        if (mPageChangeListener != null) {
                            mPageChangeListener.onCategoryFinish(bookShelfBean.getChapterList());
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        changePageStatus(STATUS_PARSE_ERROR);
                    }
                });
    }

    @Override
    protected BufferedReader getChapterReader(ChapterListBean chapter) throws Exception {
        String content = getChapterContent(chapter);
        return new BufferedReader(new StringReader(content));
    }

    @Override
    protected boolean hasChapterData(ChapterListBean chapter) {
        return true;
    }
}
