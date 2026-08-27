const XML_HEADERS = {
  "content-type": "application/xml; charset=utf-8",
  "access-control-allow-origin": "*",
};
const CACHE_VERSION = "3";

export default {
  async fetch(request, env, context) {
    const url = new URL(request.url);
    if (request.method !== "GET") {
      return new Response("Method not allowed", { status: 405 });
    }

    let upstream;
    let cacheSeconds;
    if (url.pathname === "/station-search") {
      const query = clean(url.searchParams.get("query"));
      if (!query) return new Response("query is required", { status: 400 });
      requireSecret(env.SEOUL_OPEN_DATA_KEY, "SEOUL_OPEN_DATA_KEY");
      upstream =
        `http://openapi.seoul.go.kr:8088/${encodeURIComponent(env.SEOUL_OPEN_DATA_KEY)}` +
        `/xml/SearchSTNBySubwayLineInfo/1/100/%20/${encodeURIComponent(query)}/`;
      cacheSeconds = 3600;
    } else if (url.pathname === "/arrivals") {
      const station = clean(url.searchParams.get("station"));
      if (!station) return new Response("station is required", { status: 400 });
      requireSecret(env.SEOUL_SUBWAY_KEY, "SEOUL_SUBWAY_KEY");
      upstream =
        `http://swopenapi.seoul.go.kr/api/subway/${encodeURIComponent(env.SEOUL_SUBWAY_KEY)}` +
        `/xml/realtimeStationArrival/0/100/${encodeURIComponent(station)}`;
      cacheSeconds = 20;
    } else if (url.pathname === "/seoul-bus/station-search") {
      const query = clean(url.searchParams.get("query"));
      if (!query) return new Response("query is required", { status: 400 });
      requireSecret(env.PUBLIC_DATA_SERVICE_KEY, "PUBLIC_DATA_SERVICE_KEY");
      upstream =
        "http://ws.bus.go.kr/api/rest/stationinfo/getStationByName" +
        `?serviceKey=${encodeURIComponent(env.PUBLIC_DATA_SERVICE_KEY)}` +
        `&stSrch=${encodeURIComponent(query)}`;
      cacheSeconds = 3600;
    } else if (url.pathname === "/seoul-bus/station-arrivals") {
      const arsId = clean(url.searchParams.get("arsId"));
      if (!arsId) return new Response("arsId is required", { status: 400 });
      requireSecret(env.PUBLIC_DATA_SERVICE_KEY, "PUBLIC_DATA_SERVICE_KEY");
      upstream =
        "http://ws.bus.go.kr/api/rest/stationinfo/getStationByUid" +
        `?serviceKey=${encodeURIComponent(env.PUBLIC_DATA_SERVICE_KEY)}` +
        `&arsId=${encodeURIComponent(arsId)}`;
      cacheSeconds = 20;
    } else if (url.pathname === "/seoul-bus/routes") {
      const arsId = clean(url.searchParams.get("arsId"));
      if (!arsId) return new Response("arsId is required", { status: 400 });
      requireSecret(env.PUBLIC_DATA_SERVICE_KEY, "PUBLIC_DATA_SERVICE_KEY");
      upstream =
        "http://ws.bus.go.kr/api/rest/stationinfo/getRouteByStation" +
        `?serviceKey=${encodeURIComponent(env.PUBLIC_DATA_SERVICE_KEY)}` +
        `&arsId=${encodeURIComponent(arsId)}`;
      cacheSeconds = 3600;
    } else {
      return new Response("Not found", { status: 404 });
    }

    const cache = caches.default;
    const cacheUrl = new URL(url);
    cacheUrl.searchParams.set("__cache_version", CACHE_VERSION);
    const cacheKey = new Request(cacheUrl.toString(), request);
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    const response = await fetch(upstream, {
      headers: { accept: "application/xml" },
      cf: { cacheTtl: cacheSeconds, cacheEverything: true },
    });
    const safeResponse = new Response(response.body, {
      status: response.status,
      headers: {
        ...XML_HEADERS,
        "cache-control": `public, max-age=${cacheSeconds}`,
      },
    });
    if (response.ok) context.waitUntil(cache.put(cacheKey, safeResponse.clone()));
    return safeResponse;
  },
};

function clean(value) {
  return (value || "").trim().slice(0, 40);
}

function requireSecret(value, name) {
  if (!value) throw new Error(`${name} secret is not configured`);
}
