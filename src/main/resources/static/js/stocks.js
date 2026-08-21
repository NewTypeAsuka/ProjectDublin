// 관심 종목 조회와 전체 시장 티커 검색 및 일별 주가 그래프 표시
// 사용처: stocks.html

document.addEventListener('DOMContentLoaded', () => {
    const searchForm = document.getElementById('stock-search-form');
    const searchInput = document.getElementById('stock-search-input');
    const searchToggle = searchForm?.querySelector('.stock-search__toggle');
    const resetButton = document.getElementById('stock-search-reset');
    const retryButton = document.getElementById('stock-retry-button');
    const list = document.getElementById('stock-list');
    const loading = document.getElementById('stock-loading');
    const errorState = document.getElementById('stock-error');
    const errorMessage = document.getElementById('stock-error-message');
    const emptyState = document.getElementById('stock-empty');
    const status = document.getElementById('stock-status');
    const viewLabel = document.getElementById('stock-view-label');
    const stockCount = document.getElementById('stock-count');
    const messageConfig = document.getElementById('stock-messages');

    if (!searchForm || !searchInput || !searchToggle || !resetButton
            || !retryButton || !list || !loading || !errorState
            || !errorMessage || !emptyState || !status || !viewLabel
            || !stockCount || !messageConfig) {
        return;
    }

    const messages = messageConfig.dataset;
    const displayLocale = document.documentElement.lang === 'ja' ? 'ja-JP' : 'ko-KR';
    const marketNames = {
        US: messages.marketUs,
        JP: messages.marketJp,
        KR: messages.marketKr,
        HK: messages.marketHk,
        CN: messages.marketCn
    };
    const tickerPattern = /^[A-Za-z0-9.\-]+$/;
    const svgNamespace = 'http://www.w3.org/2000/svg';
    let watchlistSnapshot = null;
    let activeRequest = {
        endpoint: '/api/stocks',
        mode: 'watchlist',
        query: ''
    };
    let requestController = null;

    // 검색창의 표시 상태와 키보드 접근성을 게시글 검색창과 동일하게 관리합니다.
    function setSearchOpen(open, focusInput) {
        searchForm.classList.toggle('is-open', open);
        searchToggle.setAttribute('aria-expanded', String(open));
        const toggleLabel = open ? messages.searchAction : messages.searchOpen;
        searchToggle.setAttribute('aria-label', toggleLabel);
        searchToggle.title = toggleLabel;
        searchInput.tabIndex = open ? 0 : -1;
        searchInput.setAttribute('aria-hidden', String(!open));

        if (open && focusInput) {
            window.requestAnimationFrame(() => searchInput.focus());
        }
    }

    function setSearchMode(request) {
        const searching = request.mode === 'search';
        resetButton.hidden = !searching;
        searchForm.classList.toggle('has-search-results', searching);
    }

    function createElement(tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) {
            element.className = className;
        }
        if (text !== undefined) {
            element.textContent = text;
        }
        return element;
    }

    function formatMessage(template, ...values) {
        return values.reduce(
            (result, value, index) => result.split(`{${index}}`).join(String(value)),
            template
        );
    }

    function setStatus(message, state = '') {
        status.textContent = message;
        status.className = 'stock-status';
        if (state) {
            status.classList.add(`is-${state}`);
        }
        status.hidden = !message;
    }

    function setLoading(isLoading) {
        loading.hidden = !isLoading;
        list.setAttribute('aria-busy', String(isLoading));
        if (isLoading) {
            errorState.hidden = true;
            emptyState.hidden = true;
            list.replaceChildren();
            stockCount.textContent = '0';
            setStatus('');
        }
    }

    function showError(message) {
        setLoading(false);
        list.replaceChildren();
        emptyState.hidden = true;
        errorMessage.textContent = message;
        errorState.hidden = false;
        stockCount.textContent = '0';
        setStatus('');
    }

    function currencyDigits(currency) {
        return currency === 'KRW' || currency === 'JPY' ? 0 : 2;
    }

    function parseNumber(value) {
        if (value === null || value === undefined || value === '') {
            return null;
        }
        const number = Number(value);
        return Number.isFinite(number) ? number : null;
    }

    function formatMoney(value, currency) {
        const number = parseNumber(value);
        if (number === null) {
            return '-';
        }

        const digits = currencyDigits(currency);
        try {
            return new Intl.NumberFormat(displayLocale, {
                style: 'currency',
                currency: currency || 'USD',
                minimumFractionDigits: digits,
                maximumFractionDigits: digits
            }).format(number);
        } catch (error) {
            return `${number.toLocaleString(displayLocale, {
                maximumFractionDigits: digits
            })} ${currency || ''}`.trim();
        }
    }

    function formatSignedMoney(value, currency) {
        const number = parseNumber(value);
        if (number === null) {
            return '-';
        }
        const sign = number > 0 ? '+' : number < 0 ? '-' : '';
        return `${sign}${formatMoney(Math.abs(number), currency)}`;
    }

    function formatPercent(value) {
        const number = parseNumber(value);
        if (number === null) {
            return '-';
        }
        const sign = number > 0 ? '+' : '';
        return `${sign}${number.toFixed(2)}%`;
    }

    function formatQuotedAt(value) {
        if (!value) {
            return messages.quoteTimeUnavailable;
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return messages.quoteTimeUnavailable;
        }
        return `${new Intl.DateTimeFormat(displayLocale, {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
            timeZone: 'Asia/Seoul'
        }).format(date)} KST`;
    }

    function getChangeState(value) {
        const change = parseNumber(value);
        if (change === null) {
            return 'flat';
        }
        if (change > 0) {
            return 'positive';
        }
        if (change < 0) {
            return 'negative';
        }
        return 'flat';
    }

    function createChart(pricePoints, changeState, ticker) {
        const figure = createElement('figure', 'stock-chart');
        const label = createElement('div', 'stock-chart__label');
        label.append(
            createElement('span', '', messages.chartPeriod),
            createElement('span', '', messages.chartClose)
        );
        figure.append(label);

        const points = Array.isArray(pricePoints)
            ? pricePoints
                .map(point => parseNumber(point && point.price))
                .filter(point => point !== null)
            : [];
        if (points.length < 2) {
            figure.append(createElement(
                'div',
                'stock-chart__empty',
                messages.chartEmpty
            ));
            return figure;
        }

        const width = 320;
        const height = 96;
        const padding = 7;
        const minimum = Math.min(...points);
        const maximum = Math.max(...points);
        const range = maximum - minimum || 1;
        const coordinates = points.map((price, index) => {
            const x = padding + index * (width - padding * 2) / (points.length - 1);
            const y = height - padding
                - (price - minimum) / range * (height - padding * 2);
            return {x, y};
        });

        const svg = document.createElementNS(svgNamespace, 'svg');
        svg.setAttribute('class', 'stock-chart__svg');
        svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
        svg.setAttribute('role', 'img');
        svg.setAttribute('aria-label', formatMessage(messages.chartAria, ticker));
        svg.setAttribute('preserveAspectRatio', 'none');

        const baseline = document.createElementNS(svgNamespace, 'line');
        baseline.setAttribute('class', 'stock-chart__baseline');
        baseline.setAttribute('x1', String(padding));
        baseline.setAttribute('x2', String(width - padding));
        baseline.setAttribute('y1', String(height / 2));
        baseline.setAttribute('y2', String(height / 2));

        const line = document.createElementNS(svgNamespace, 'path');
        line.setAttribute(
            'class',
            `stock-chart__line is-${changeState}`
        );
        line.setAttribute(
            'd',
            coordinates.map((point, index) =>
                `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`
            ).join(' ')
        );

        const lastPoint = coordinates[coordinates.length - 1];
        const point = document.createElementNS(svgNamespace, 'circle');
        point.setAttribute(
            'class',
            `stock-chart__point is-${changeState}`
        );
        point.setAttribute('cx', lastPoint.x.toFixed(2));
        point.setAttribute('cy', lastPoint.y.toFixed(2));
        point.setAttribute('r', '3.5');

        svg.append(baseline, line, point);
        figure.append(svg);
        return figure;
    }

    function createStockCard(stock) {
        const changeState = getChangeState(stock.priceChange);
        const card = createElement('article', 'stock-card');
        card.dataset.symbol = stock.symbol || '';

        const header = createElement('div', 'stock-card__header');
        const identity = createElement('div', 'stock-card__identity');
        identity.append(
            createElement('h3', 'stock-card__ticker', stock.ticker || stock.symbol || '-'),
            createElement(
                'span',
                'stock-card__market',
                marketNames[String(stock.market || '').toUpperCase()] || stock.market || '-'
            )
        );

        const quotedTime = createElement(
            'time',
            'stock-card__time',
            formatQuotedAt(stock.quotedAt)
        );
        quotedTime.dateTime = stock.quotedAt || '';
        quotedTime.title = messages.quoteTimeTitle;
        header.append(identity, quotedTime);

        const body = createElement('div', 'stock-card__body');
        const quote = createElement('div', 'stock-card__quote');
        quote.append(
            createElement('div', 'stock-card__name', stock.name || stock.symbol || '-'),
            createElement(
                'div',
                'stock-card__price',
                formatMoney(stock.currentPrice, stock.currency)
            )
        );

        const change = createElement(
            'div',
            `stock-card__change is-${changeState}`
        );
        const changeIcon = createElement(
            'i',
            `bi ${changeState === 'positive'
                ? 'bi-caret-up-fill'
                : changeState === 'negative'
                    ? 'bi-caret-down-fill'
                    : 'bi-dash'}`
        );
        changeIcon.setAttribute('aria-hidden', 'true');
        change.append(changeIcon);
        const hasChange = parseNumber(stock.priceChange) !== null
            && parseNumber(stock.changePercent) !== null;
        change.append(createElement(
            'span',
            '',
            hasChange
                ? `${formatSignedMoney(stock.priceChange, stock.currency)} `
                    + `(${formatPercent(stock.changePercent)})`
                : messages.changeUnavailable
        ));
        quote.append(change);

        body.append(
            quote,
            createChart(
                stock.dailyPrices,
                changeState,
                stock.ticker || stock.symbol || messages.stockFallback
            )
        );

        const footer = createElement('div', 'stock-card__footer');
        const previous = createElement('span', 'stock-card__previous');
        const previousIcon = createElement('i', 'bi bi-calendar-check');
        previousIcon.setAttribute('aria-hidden', 'true');
        previous.append(
            previousIcon,
            createElement(
                'span',
                '',
                formatMessage(
                    messages.previousClose,
                    formatMoney(stock.previousClose, stock.currency)
                )
            )
        );

        const exchange = createElement('span', 'stock-card__exchange');
        const exchangeIcon = createElement('i', 'bi bi-building');
        exchangeIcon.setAttribute('aria-hidden', 'true');
        exchange.append(
            exchangeIcon,
            createElement(
                'span',
                '',
                `${stock.exchange || '-'} · ${stock.currency || '-'}`
            )
        );
        footer.append(previous, exchange);

        card.append(header, body, footer);
        return card;
    }

    function renderCollection(data, request) {
        setLoading(false);
        errorState.hidden = true;
        list.replaceChildren();

        const stocks = Array.isArray(data.stocks) ? data.stocks : [];
        stockCount.textContent = String(stocks.length);
        viewLabel.textContent = request.mode === 'search'
            ? formatMessage(messages.searchResult, request.query)
            : messages.watchlist;
        setSearchMode(request);

        if (stocks.length === 0) {
            emptyState.hidden = false;
            setStatus('');
            return;
        }

        emptyState.hidden = true;
        const fragment = document.createDocumentFragment();
        stocks.forEach(stock => fragment.append(createStockCard(stock)));
        list.append(fragment);

        const unavailable = Array.isArray(data.unavailableSymbols)
            ? data.unavailableSymbols
            : [];
        if (data.stale === true) {
            setStatus(
                messages.statusStale,
                'warning'
            );
        } else if (unavailable.length > 0) {
            setStatus(
                formatMessage(messages.statusPartial, unavailable.join(', ')),
                'warning'
            );
        } else {
            setStatus('');
        }
    }

    async function loadCollection(request) {
        if (requestController) {
            requestController.abort();
        }
        requestController = new AbortController();
        activeRequest = request;
        setLoading(true);
        viewLabel.textContent = request.mode === 'search'
            ? formatMessage(messages.searchResult, request.query)
            : messages.watchlist;
        setSearchMode(request);

        try {
            const response = await fetch(request.endpoint, {
                method: 'GET',
                headers: {
                    Accept: 'application/json'
                },
                credentials: 'same-origin',
                signal: requestController.signal
            });
            if (!response.ok) {
                throw new Error(`Stock API response error: ${response.status}`);
            }

            const data = await response.json();
            if (!data || !Array.isArray(data.stocks)) {
                throw new Error('Invalid stock API response format.');
            }
            if (request.mode === 'watchlist') {
                watchlistSnapshot = data;
            }
            renderCollection(data, request);
        } catch (error) {
            if (error.name === 'AbortError') {
                return;
            }
            console.error(error);
            showError(messages.loadError);
        }
    }

    searchForm.addEventListener('submit', event => {
        event.preventDefault();
        if (searchToggle.getAttribute('aria-expanded') !== 'true') {
            setSearchOpen(true, true);
            return;
        }

        const query = searchInput.value.trim();
        if (!query || query.length > 15 || !tickerPattern.test(query)) {
            setStatus(
                messages.tickerInvalid,
                'error'
            );
            searchInput.focus();
            return;
        }

        const normalizedQuery = query.toUpperCase();
        searchInput.value = normalizedQuery;
        const parameters = new URLSearchParams({query: normalizedQuery});
        loadCollection({
            endpoint: `/api/stocks/search?${parameters.toString()}`,
            mode: 'search',
            query: normalizedQuery
        });
    });

    searchInput.addEventListener('input', () => {
        if (searchInput.value.length > 15) {
            searchInput.value = searchInput.value.slice(0, 15);
        }
    });

    resetButton.addEventListener('click', () => {
        searchInput.value = '';
        const watchlistRequest = {
            endpoint: '/api/stocks',
            mode: 'watchlist',
            query: ''
        };
        activeRequest = watchlistRequest;
        if (watchlistSnapshot) {
            renderCollection(watchlistSnapshot, watchlistRequest);
        } else {
            loadCollection(watchlistRequest);
        }
        setSearchOpen(false, false);
        searchToggle.focus();
    });

    document.addEventListener('click', event => {
        if (searchToggle.getAttribute('aria-expanded') === 'true'
                && !searchForm.contains(event.target)) {
            setSearchOpen(false, false);
        }
    });

    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape'
                || searchToggle.getAttribute('aria-expanded') !== 'true') {
            return;
        }

        setSearchOpen(false, false);
        searchToggle.focus();
    });

    retryButton.addEventListener('click', () => loadCollection(activeRequest));

    setSearchOpen(false, false);
    loadCollection(activeRequest);
});
