#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Product Crawler - 상품 URL에서 상품 정보를 추출하는 정적 크롤러
사용법: python product_crawler.py <url>
출력: JSON 형식의 상품 정보
"""

import sys
import json
import re
from urllib.parse import urlparse
import requests
from bs4 import BeautifulSoup


class ProductCrawler:
    """상품 정보 크롤러"""

    HEADERS = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7',
    }

    def __init__(self, url: str):
        self.url = url
        self.domain = self._extract_domain(url)
        self.soup = None

    def _extract_domain(self, url: str) -> str:
        """URL에서 도메인 추출"""
        parsed = urlparse(url)
        domain = parsed.netloc.lower()
        # www. 제거
        if domain.startswith('www.'):
            domain = domain[4:]
        return domain

    def _fetch_page(self) -> bool:
        """페이지 HTML 가져오기"""
        try:
            response = requests.get(self.url, headers=self.HEADERS, timeout=10)
            response.raise_for_status()
            response.encoding = response.apparent_encoding
            self.soup = BeautifulSoup(response.text, 'html.parser')
            return True
        except Exception as e:
            return False

    def _clean_price(self, price_text: str) -> int:
        """가격 문자열에서 숫자만 추출"""
        if not price_text:
            return 0
        # 숫자만 추출
        numbers = re.findall(r'\d+', price_text.replace(',', ''))
        if numbers:
            return int(''.join(numbers))
        return 0

    def _clean_text(self, text: str) -> str:
        """텍스트 정제"""
        if not text:
            return ''
        return ' '.join(text.split()).strip()

    def crawl(self) -> dict:
        """상품 정보 크롤링"""
        result = {
            'success': False,
            'url': self.url,
            'domain': self.domain,
            'productName': None,
            'productPrice': 0,
            'productImageUrl': None,
            'productDescription': None,
            'error': None
        }

        if not self._fetch_page():
            result['error'] = 'Failed to fetch page'
            return result

        try:
            # 도메인별 크롤링 로직
            if 'coupang.com' in self.domain:
                return self._crawl_coupang(result)
            elif '11st.co.kr' in self.domain:
                return self._crawl_11st(result)
            elif 'gmarket.co.kr' in self.domain:
                return self._crawl_gmarket(result)
            elif 'auction.co.kr' in self.domain:
                return self._crawl_auction(result)
            elif 'smartstore.naver.com' in self.domain or 'shopping.naver.com' in self.domain:
                return self._crawl_naver(result)
            else:
                # 범용 크롤링 (Open Graph 메타태그 활용)
                return self._crawl_generic(result)
        except Exception as e:
            result['error'] = str(e)
            return result

    def _crawl_coupang(self, result: dict) -> dict:
        """쿠팡 크롤링"""
        # 상품명
        name_elem = self.soup.select_one('h1.prod-buy-header__title')
        if name_elem:
            result['productName'] = self._clean_text(name_elem.get_text())

        # 가격
        price_elem = self.soup.select_one('span.total-price strong')
        if price_elem:
            result['productPrice'] = self._clean_price(price_elem.get_text())

        # 이미지
        img_elem = self.soup.select_one('img.prod-image__detail')
        if img_elem:
            result['productImageUrl'] = img_elem.get('src') or img_elem.get('data-src')

        # 설명 (Open Graph)
        desc_elem = self.soup.select_one('meta[property="og:description"]')
        if desc_elem:
            result['productDescription'] = desc_elem.get('content')

        result['success'] = bool(result['productName'])
        return result

    def _crawl_11st(self, result: dict) -> dict:
        """11번가 크롤링"""
        # 상품명
        name_elem = self.soup.select_one('h1.title') or self.soup.select_one('div.c_product_info_title h2')
        if name_elem:
            result['productName'] = self._clean_text(name_elem.get_text())

        # 가격
        price_elem = self.soup.select_one('span.value') or self.soup.select_one('dd.sale_price')
        if price_elem:
            result['productPrice'] = self._clean_price(price_elem.get_text())

        # 이미지
        img_elem = self.soup.select_one('img#mainImg') or self.soup.select_one('meta[property="og:image"]')
        if img_elem:
            result['productImageUrl'] = img_elem.get('src') or img_elem.get('content')

        # 설명
        desc_elem = self.soup.select_one('meta[property="og:description"]')
        if desc_elem:
            result['productDescription'] = desc_elem.get('content')

        result['success'] = bool(result['productName'])
        return result

    def _crawl_gmarket(self, result: dict) -> dict:
        """G마켓 크롤링"""
        # 상품명
        name_elem = self.soup.select_one('h1.itemtit') or self.soup.select_one('span.item_tit')
        if name_elem:
            result['productName'] = self._clean_text(name_elem.get_text())

        # 가격
        price_elem = self.soup.select_one('span.price_real') or self.soup.select_one('strong.price_innerwrap')
        if price_elem:
            result['productPrice'] = self._clean_price(price_elem.get_text())

        # 이미지
        img_elem = self.soup.select_one('meta[property="og:image"]')
        if img_elem:
            result['productImageUrl'] = img_elem.get('content')

        # 설명
        desc_elem = self.soup.select_one('meta[property="og:description"]')
        if desc_elem:
            result['productDescription'] = desc_elem.get('content')

        result['success'] = bool(result['productName'])
        return result

    def _crawl_auction(self, result: dict) -> dict:
        """옥션 크롤링"""
        # 상품명
        name_elem = self.soup.select_one('h1.itemtit') or self.soup.select_one('span.item_tit')
        if name_elem:
            result['productName'] = self._clean_text(name_elem.get_text())

        # 가격
        price_elem = self.soup.select_one('span.price_real') or self.soup.select_one('strong.price')
        if price_elem:
            result['productPrice'] = self._clean_price(price_elem.get_text())

        # 이미지
        img_elem = self.soup.select_one('meta[property="og:image"]')
        if img_elem:
            result['productImageUrl'] = img_elem.get('content')

        # 설명
        desc_elem = self.soup.select_one('meta[property="og:description"]')
        if desc_elem:
            result['productDescription'] = desc_elem.get('content')

        result['success'] = bool(result['productName'])
        return result

    def _crawl_naver(self, result: dict) -> dict:
        """네이버 스마트스토어/쇼핑 크롤링"""
        # Open Graph 메타태그 활용
        name_elem = self.soup.select_one('meta[property="og:title"]')
        if name_elem:
            result['productName'] = self._clean_text(name_elem.get('content'))

        # 가격 - 스마트스토어
        price_elem = self.soup.select_one('span._1LY7DqS2As') or self.soup.select_one('meta[property="product:price:amount"]')
        if price_elem:
            price_text = price_elem.get('content') if price_elem.name == 'meta' else price_elem.get_text()
            result['productPrice'] = self._clean_price(price_text)

        # 이미지
        img_elem = self.soup.select_one('meta[property="og:image"]')
        if img_elem:
            result['productImageUrl'] = img_elem.get('content')

        # 설명
        desc_elem = self.soup.select_one('meta[property="og:description"]')
        if desc_elem:
            result['productDescription'] = desc_elem.get('content')

        result['success'] = bool(result['productName'])
        return result

    def _crawl_generic(self, result: dict) -> dict:
        """범용 크롤링 (Open Graph 메타태그 기반)"""
        # Open Graph 메타태그
        og_title = self.soup.select_one('meta[property="og:title"]')
        og_image = self.soup.select_one('meta[property="og:image"]')
        og_desc = self.soup.select_one('meta[property="og:description"]')

        # 상품명
        if og_title:
            result['productName'] = self._clean_text(og_title.get('content'))
        else:
            title_elem = self.soup.select_one('title')
            if title_elem:
                result['productName'] = self._clean_text(title_elem.get_text())

        # 이미지
        if og_image:
            result['productImageUrl'] = og_image.get('content')

        # 설명
        if og_desc:
            result['productDescription'] = og_desc.get('content')

        # 가격 - product:price 메타태그 또는 일반적인 가격 패턴 검색
        price_meta = self.soup.select_one('meta[property="product:price:amount"]')
        if price_meta:
            result['productPrice'] = self._clean_price(price_meta.get('content'))
        else:
            # 가격 패턴 검색 (원, 원 포함하는 텍스트)
            price_patterns = self.soup.find_all(string=re.compile(r'[\d,]+\s*원'))
            if price_patterns:
                result['productPrice'] = self._clean_price(price_patterns[0])

        result['success'] = bool(result['productName'])
        return result


def main():
    if len(sys.argv) < 2:
        print(json.dumps({
            'success': False,
            'error': 'URL argument required'
        }, ensure_ascii=False))
        sys.exit(1)

    url = sys.argv[1]
    crawler = ProductCrawler(url)
    result = crawler.crawl()

    print(json.dumps(result, ensure_ascii=False))
    sys.exit(0 if result['success'] else 1)


if __name__ == '__main__':
    main()
