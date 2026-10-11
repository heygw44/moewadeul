// 카카오맵 JS SDK 중에서 지도 래퍼가 쓰는 API만 타입으로 적는다.
// SDK는 script 태그로 불러와 전역 kakao 객체로 들어오므로 모듈이 아닌 전역 선언으로 둔다.

declare namespace kakao.maps {
  class LatLng {
    constructor(lat: number, lng: number);
    getLat(): number;
    getLng(): number;
  }

  class LatLngBounds {
    constructor(sw?: LatLng, ne?: LatLng);
    getSouthWest(): LatLng;
    getNorthEast(): LatLng;
  }

  // SDK 안에서는 숫자 상수다. 값은 kakao.maps.MapTypeId에서 이름으로 꺼내 쓴다.
  type MapTypeId = number;

  const MapTypeId: {
    readonly ROADMAP: MapTypeId;
    readonly SKYVIEW: MapTypeId;
    readonly HYBRID: MapTypeId;
    readonly TERRAIN: MapTypeId;
  };

  interface MapOptions {
    center: LatLng;
    level?: number;
  }

  interface SetLevelOptions {
    animate?: boolean | { duration: number };
    anchor?: LatLng;
  }

  class Map {
    constructor(container: HTMLElement, options: MapOptions);
    setCenter(latlng: LatLng): void;
    getCenter(): LatLng;
    setLevel(level: number, options?: SetLevelOptions): void;
    getLevel(): number;
    getBounds(): LatLngBounds;
    relayout(): void;
    addOverlayMapTypeId(mapTypeId: MapTypeId): void;
    removeOverlayMapTypeId(mapTypeId: MapTypeId): void;
  }

  interface CustomOverlayOptions {
    clickable?: boolean;
    content: HTMLElement | string;
    map?: Map;
    position: LatLng;
    xAnchor?: number;
    yAnchor?: number;
    zIndex?: number;
  }

  class CustomOverlay {
    constructor(options: CustomOverlayOptions);
    setMap(map: Map | null): void;
    getMap(): Map | null;
    setZIndex(zIndex: number): void;
    getZIndex(): number;
    setPosition(position: LatLng): void;
  }

  interface MouseEvent {
    latLng: LatLng;
  }

  namespace event {
    function addListener(target: Map, type: 'idle', handler: () => void): void;
    function removeListener(target: Map, type: 'idle', handler: () => void): void;
    function addListener(target: Map, type: 'click', handler: (event: MouseEvent) => void): void;
    function removeListener(target: Map, type: 'click', handler: (event: MouseEvent) => void): void;
  }

  // 스크립트 주소에 autoload=false를 붙여 불러왔을 때, SDK 나머지 파일을 받은 뒤 callback을 부른다.
  function load(callback: () => void): void;

  // 스크립트 주소에 libraries=services를 붙여야 들어오는 장소 검색·주소 변환 API다.
  // 좌표는 문자열로 오고, x가 경도, y가 위도다.
  namespace services {
    type Status = 'OK' | 'ZERO_RESULT' | 'ERROR';

    const Status: {
      readonly OK: Status;
      readonly ZERO_RESULT: Status;
      readonly ERROR: Status;
    };

    interface RegionAddress {
      region_1depth_name: string;
      region_2depth_name: string;
      region_3depth_name: string;
    }

    interface AddressResult {
      address_name: string;
      address_type: 'REGION' | 'ROAD' | 'REGION_ADDR' | 'ROAD_ADDR';
      x: string;
      y: string;
      address: RegionAddress | null;
    }

    interface PlaceResult {
      place_name: string;
      address_name: string;
      road_address_name: string;
      x: string;
      y: string;
    }

    interface AddressSearchOptions {
      size?: number;
    }

    interface KeywordSearchOptions {
      size?: number;
    }

    class Geocoder {
      addressSearch(
        address: string,
        callback: (result: AddressResult[], status: Status) => void,
        options?: AddressSearchOptions,
      ): void;
    }

    class Places {
      keywordSearch(
        keyword: string,
        callback: (result: PlaceResult[], status: Status) => void,
        options?: KeywordSearchOptions,
      ): void;
    }
  }
}

// 스크립트를 불러오기 전에는 전역 kakao가 없으므로 window에서 있는지 먼저 확인한다.
interface Window {
  kakao?: typeof kakao;
}
