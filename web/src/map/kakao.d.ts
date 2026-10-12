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
    extend(latlng: LatLng): void;
    getSouthWest(): LatLng;
    getNorthEast(): LatLng;
  }

  // 지도 컨테이너 왼쪽 위를 원점으로 한 픽셀 좌표다.
  class Point {
    constructor(x: number, y: number);
    x: number;
    y: number;
  }

  class MapProjection {
    containerPointFromCoords(latlng: LatLng): Point;
    coordsFromContainerPoint(point: Point): LatLng;
  }

  // SDK 안에서는 숫자 상수다. 값은 kakao.maps.MapTypeId에서 이름으로 꺼내 쓴다.
  type MapTypeId = number;

  const MapTypeId: {
    readonly ROADMAP: MapTypeId;
    readonly SKYVIEW: MapTypeId;
    readonly HYBRID: MapTypeId;
    readonly TERRAIN: MapTypeId;
  };

  // 지도 저작권·카카오 로고 자리다. MapTypeId처럼 SDK 안에서는 숫자 상수다.
  type CopyrightPosition = number;

  const CopyrightPosition: {
    readonly BOTTOMLEFT: CopyrightPosition;
    readonly BOTTOMRIGHT: CopyrightPosition;
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
    setBounds(bounds: LatLngBounds, paddingTop?: number, paddingRight?: number, paddingBottom?: number, paddingLeft?: number): void;
    getLevel(): number;
    getBounds(): LatLngBounds;
    getProjection(): MapProjection;
    relayout(): void;
    addOverlayMapTypeId(mapTypeId: MapTypeId): void;
    removeOverlayMapTypeId(mapTypeId: MapTypeId): void;
    setCopyrightPosition(position: CopyrightPosition, reversed?: boolean): void;
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
    // 지도 컨테이너 왼쪽 위를 원점으로 한 픽셀 좌표다.
    point: Point;
  }

  namespace event {
    function addListener(target: Map, type: 'idle', handler: () => void): void;
    function removeListener(target: Map, type: 'idle', handler: () => void): void;
    function addListener(target: Map, type: 'click' | 'rightclick', handler: (event: MouseEvent) => void): void;
    function removeListener(target: Map, type: 'click' | 'rightclick', handler: (event: MouseEvent) => void): void;
    function addListener(target: Map, type: 'dragstart' | 'zoom_start', handler: () => void): void;
    function removeListener(target: Map, type: 'dragstart' | 'zoom_start', handler: () => void): void;
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

    // 지번 주소다. 번지가 없는 행정구역 결과는 main_address_no가 빈 문자열이다.
    interface JibunAddress extends RegionAddress {
      address_name: string;
      main_address_no: string;
      sub_address_no: string;
      mountain_yn: 'Y' | 'N';
    }

    // 도로명 주소다. 도로 이름만 찾은 결과는 main_building_no가 빈 문자열이다.
    interface RoadAddress extends RegionAddress {
      address_name: string;
      road_name: string;
      main_building_no: string;
      sub_building_no: string;
      building_name: string;
    }

    // REGION은 행정구역, ROAD는 도로 이름, REGION_ADDR은 지번 주소, ROAD_ADDR은 도로명 주소로 찾은 결과다.
    interface AddressResult {
      address_name: string;
      address_type: 'REGION' | 'ROAD' | 'REGION_ADDR' | 'ROAD_ADDR';
      x: string;
      y: string;
      address: JibunAddress | null;
      road_address: RoadAddress | null;
    }

    // 좌표가 속한 행정구역이다. H는 행정동, B는 법정동 기준이다.
    interface RegionCodeResult extends RegionAddress {
      region_type: 'H' | 'B';
    }

    // 좌표에 있는 주소다. 도로명 주소는 건물이 없는 곳(산, 들)에서 null이다.
    interface Coord2AddressResult {
      address: { address_name: string } | null;
      road_address: { address_name: string; building_name: string } | null;
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
      coord2RegionCode(lng: number, lat: number, callback: (result: RegionCodeResult[], status: Status) => void): void;
      coord2Address(lng: number, lat: number, callback: (result: Coord2AddressResult[], status: Status) => void): void;
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
