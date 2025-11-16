package id.segari.ortools.ortool;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import id.segari.ortools.dto.SegariRouteDTO;
import id.segari.ortools.dto.SegariRouteOrderDTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.exception.BaseException;
import id.segari.ortools.external.LatLong;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.external.OSRMTableResponseDTO;
import id.segari.ortools.util.GeoUtils;
import org.springframework.http.HttpStatus;

import java.util.*;

public class SegariRoute {
    private final SegariRouteType type;
    private final List<SegariRouteOrderDTO> orders;

    private SegariRoute(SegariRouteType type, List<SegariRouteOrderDTO> orders) {
        this.type = type;
        this.orders = orders;
    }

    private void setVehicleNumbers(int vehicleNumbers) {
        this.vehicleNumbers = vehicleNumbers;
    }

    private void setMaxTotalDistanceInMeter(int maxTotalDistanceInMeter) {
        this.maxTotalDistanceInMeter = maxTotalDistanceInMeter;
    }

    private void setMaxOrderCount(int maxOrderCount) {
        this.maxOrderCount = maxOrderCount;
    }

    private void setTspFixStartIndex(int tspFixStartIndex) {
        this.tspFixStartIndex = tspFixStartIndex;
    }

    private boolean hasDistanceBetweenOrderDimension = false;
    private boolean hasDistanceWithSpDimension = false;
    private boolean hasDistanceBetweenNodeDimension = false;
    private boolean hasMaxInstanOrderCountDimension = false;
    private boolean hasMaxTurboOrderCountDimension = false;
    private boolean hasLoadFactorDimension = false;
    private boolean hasExtensionTurboInstanRatioDimension = false;
    private boolean hasSetResultMinimum = false;
    private boolean hasTimeWindowDimension = false;
    private int minimumResult = 0;
    private boolean hasResultMustContainExtension = false;
    private long[][] distanceMatrix;
    private long[][] durationMatrix;
    private int[] start;
    private int[] finish;
    private int vehicleNumbers;
    private long[] maxOrderDemands;
    private long[] maxOrderVehicleCapacities;
    private long[] maxTurboDemands;
    private long[] maxTurboVehicleCapacities;
    private long[] maxInstanDemands;
    private long[] maxInstanVehicleCapacities;
    private long[] loadFactorDemands;
    private long[] loadFactorVehicleCapacities;
    private long[] extensionRatioDemands;
    private long[] extensionRatioVehicleCapacities;
    private long[][] timeWindows;
    private int maxTotalDistanceInMeter;
    private int maxDistanceBetweenOrderInMeter;
    private int maxDistanceWithSpInMeter;
    private int maxDistanceBetweenNodeInMeter;
    private int maxOrderCount;
    private int maxInstanOrderCount;
    private int maxTurboOrderCount;
    private int maxLoadFactor;
    private int extensionRatio;
    private int turboInstanRatio;
    private int extensionCount;
    private int tspFixStartIndex;

    private OSRMRestService osrmRestService;
    private boolean useOsrm;
    private final Set<Long> mandatoryOrderIds = new HashSet<>();

    public void setOsrmRestService(OSRMRestService osrmRestService) {
        this.osrmRestService = osrmRestService;
        this.useOsrm = true;
    }

    private void setMandatoryOrderIds(Set<Long> mandatoryOrderIds) {
        this.mandatoryOrderIds.addAll(mandatoryOrderIds);
    }

    public static SegariRoute newVrpStartFromSpAndArbitraryFinish(SegariRouteDTO dto, OSRMRestService osrmRestService){
        if (dto.orders().size() <= 2) throw SegariRoutingErrors.emptyOrder();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(dto.orders().get(0).type())) throw SegariRoutingErrors.indexZeroNotDummy();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(dto.orders().get(1).type())) throw SegariRoutingErrors.indexOneNotSp();
        SegariRoute segariRoute = new SegariRoute(SegariRouteType.VRP_SP_START_ARBITRARY_FINISH, dto.orders());
        injectVrpAttributes(segariRoute, dto.maxTotalDistanceInMeter(), dto.maxOrderCount(), dto.orders().size() - 2);
        segariRoute.setOsrmRestService(osrmRestService);
        return segariRoute;
    }

    public static SegariRoute newVrpWithArbitraryStartAndFinish(SegariRouteDTO dto, OSRMRestService osrmRestService){
        if (dto.orders().size() <= 1) throw SegariRoutingErrors.emptyOrder();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(dto.orders().get(0).type())) throw SegariRoutingErrors.indexZeroNotDummy();
        SegariRoute segariRoute = new SegariRoute(SegariRouteType.VRP_ARBITRARY_START_AND_FINISH, dto.orders());
        injectVrpAttributes(segariRoute, dto.maxTotalDistanceInMeter(), dto.maxOrderCount(), dto.orders().size() - 1);
        segariRoute.setOsrmRestService(osrmRestService);
        return segariRoute;
    }

    public static SegariRoute newTspWithStartAndFinish(SegariRouteDTO dto, int startIndex, OSRMRestService osrmRestService){
        if (dto.orders().size() <= 1) throw SegariRoutingErrors.emptyOrder();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(dto.orders().get(0).type())) throw SegariRoutingErrors.indexZeroNotDummy();
        SegariRoute segariRoute = new SegariRoute(SegariRouteType.TSP_FIX_START_ARBITRARY_FINISH, dto.orders());
        injectTspAttributes(segariRoute, dto.maxTotalDistanceInMeter(), dto.maxOrderCount(), startIndex);
        segariRoute.setOsrmRestService(osrmRestService);
        return segariRoute;
    }

    public static SegariRoute newTspWithSpStartAndArbitraryFinish(SegariRouteDTO dto, OSRMRestService osrmRestService){
        if (dto.orders().size() <= 1) throw SegariRoutingErrors.emptyOrder();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(dto.orders().get(0).type())) throw SegariRoutingErrors.indexZeroNotDummy();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(dto.orders().get(1).type())) throw SegariRoutingErrors.indexOneNotSp();
        SegariRoute segariRoute = new SegariRoute(SegariRouteType.TSP_SP_START_ARBITRARY_FINISH, dto.orders());
        injectTspAttributes(segariRoute, dto.maxTotalDistanceInMeter(), dto.maxOrderCount(), 1);
        segariRoute.setOsrmRestService(osrmRestService);
        if (Objects.nonNull(dto.mandatoryOrders()) && !dto.mandatoryOrders().isEmpty()){
            segariRoute.setMandatoryOrderIds(dto.mandatoryOrders());
        }
        if (dto.useTimeWindow()){
            segariRoute.addTimeWindowDimension(dto);
        }
        return segariRoute;
    }

    public SegariRoute alterVehicleNumbers(int vehicleNumbers){
        if (vehicleNumbers <= 0) throw SegariRoutingErrors.invalidRoutingParameter("vehicleNumbers in alterVehicleNumbers");
        this.setVehicleNumbers(vehicleNumbers);
        return this;
    }

    public SegariRoute addDistanceBetweenOrderDimension(int maxDistanceInMeter){
        if (this.hasDistanceBetweenNodeDimension) throw SegariRoutingErrors.cannotUseAddDistanceBetweenOrderDimension();
        if (maxDistanceInMeter <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxDistanceInMeter in addDistanceBetweenOrderDimension");
        this.hasDistanceBetweenOrderDimension = true;
        this.maxDistanceBetweenOrderInMeter = maxDistanceInMeter;
        return this;
    }

    public SegariRoute addDistanceWithSpDimension(int maxDistanceInMeter){
        if (this.hasDistanceBetweenNodeDimension) throw SegariRoutingErrors.cannotUseAddDistanceWithSpDimension();
        if (maxDistanceInMeter <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxDistanceInMeter in addDistanceWithSpDimension");
        this.hasDistanceWithSpDimension = true;
        this.maxDistanceWithSpInMeter = maxDistanceInMeter;
        return this;
    }

    public SegariRoute addDistanceBetweenNodeDimension(int maxDistanceInMeter){
        if (this.hasDistanceBetweenOrderDimension || this.hasDistanceWithSpDimension) throw SegariRoutingErrors.cannotUseAddDistanceBetweenNodeDimension();
        if (maxDistanceInMeter <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxDistanceInMeter in addDistanceBetweenNodeDimension");
        this.hasDistanceBetweenNodeDimension = true;
        this.maxDistanceBetweenNodeInMeter = maxDistanceInMeter;
        return this;
    }

    public SegariRoute addTimeWindowDimension(SegariRouteDTO dto){
        if (!useOsrm) throw new RuntimeException();
        this.hasTimeWindowDimension = true;
        this.timeWindows = new long[dto.orders().size()][2];
        for (int i = 0; i < dto.orders().size(); i++) {
            timeWindows[i][0] = 0;
            timeWindows[i][1] = dto.orders().get(i).maxTimeWindow();
        }
        return this;
    }

    public SegariRoute addMaxInstanOrderCountDimension(int max){
        this.hasMaxInstanOrderCountDimension = true;
        this.maxInstanOrderCount = max;
        return this;
    }

    public SegariRoute addMaxTurboOrderCountDimension(int max){
        this.hasMaxTurboOrderCountDimension = true;
        this.maxTurboOrderCount = max;
        return this;
    }

    /**
     * intended for load factor which right now is not possible,
     * set it private for now
     * @param max
     * @return this
     */
    private SegariRoute addLoadFactorDimension(int max){
        this.hasLoadFactorDimension = true;
        this.maxLoadFactor = max;
        return this;
    }

    public SegariRoute addExtensionTurboInstanRatioDimension(int extensionRatio, int turboInstanRatio, int extensionCount){
        this.hasExtensionTurboInstanRatioDimension = true;
        if (extensionRatio <= 0 || turboInstanRatio <= 0 || extensionCount <= 0)
            throw SegariRoutingErrors.invalidRoutingParameter("extensionRatio or turboInstanRatio or extensionCount in addExtensionTurboInstanRatioDimension");
        this.extensionRatio = extensionRatio;
        this.turboInstanRatio = turboInstanRatio;
        this.extensionCount = extensionCount;
        return this;
    }

    public SegariRoute setResultMinimum(int minimum){
        if (minimum > maxOrderCount) throw SegariRoutingErrors.invalidRoutingParameter("minimumResult cannot greater than maxOrderCount");
        this.hasSetResultMinimum = true;
        this.minimumResult = minimum;
        return this;
    }

    public SegariRoute setResultMustContainExtension(){
        this.hasResultMustContainExtension = true;
        return this;
    }

    public List<ArrayList<Long>> route(){
        try {
            return handleRoute();
        }
        catch (Exception e){
          throw BaseException.builder()
                  .message(e.getMessage())
                  .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                  .build();
        }
    }

    private List<ArrayList<Long>> handleRoute() {
        fillRequiredAttributes();
        RoutingIndexManager manager = getRoutingIndexManager();
        RoutingModel routing = new RoutingModel(manager);
        addDistanceDimension(routing, manager);
        addMaxOrderCountDimension(routing, manager);
        if (this.hasTimeWindowDimension) addTimeWindowDimension(routing, manager);
        if (this.hasExtensionTurboInstanRatioDimension) addExtensionTurboInstanRatioDimension(routing, manager);
        if (this.hasMaxInstanOrderCountDimension) addMaxInstanOrderCountDimension(routing, manager);
        if (this.hasMaxTurboOrderCountDimension) addMaxTurboOrderCountDimension(routing, manager);
        if (this.hasLoadFactorDimension) addLoadFactorDimension(routing, manager);
        addPenaltyAndDropVisit(routing, manager);
        Assignment solution = findSolution(routing);
        return getResult(routing, manager, solution);
    }


    private RoutingIndexManager getRoutingIndexManager() {
        if (this.distanceMatrix.length == 0) throw SegariRoutingErrors.invalidRoutingParameter("distanceMatrix in getRoutingIndexManager");
        if (this.vehicleNumbers <= 0) throw SegariRoutingErrors.invalidRoutingParameter("vehicleNumbers in getRoutingIndexManager");
        if (this.start.length == 0) throw SegariRoutingErrors.invalidRoutingParameter("start in getRoutingIndexManager");
        if (this.finish.length == 0) throw SegariRoutingErrors.invalidRoutingParameter("finish in getRoutingIndexManager");
        return new RoutingIndexManager(this.distanceMatrix.length, this.vehicleNumbers, this.start, this.finish);
    }

    private void addDistanceDimension(RoutingModel routing, RoutingIndexManager manager) {
        if (this.maxTotalDistanceInMeter <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxTotalDistanceInMeter in addDistanceDimension");
        if (this.distanceMatrix.length == 0) throw SegariRoutingErrors.invalidRoutingParameter("distanceMatrix in addDistanceDimension");
        final int transitCallbackIndex =
                routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                    int fromNode = manager.indexToNode(fromIndex);
                    int toNode = manager.indexToNode(toIndex);
                    return distanceMatrix[fromNode][toNode];
                });
        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);
        routing.addDimension(transitCallbackIndex, 0, this.maxTotalDistanceInMeter,
                true,
                "Distance");
    }

    private void addTimeWindowDimension(RoutingModel routing, RoutingIndexManager manager) {
        final int transitCallbackIndex =
                routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                    int fromNode = manager.indexToNode(fromIndex);
                    int toNode = manager.indexToNode(toIndex);
                    return durationMatrix[fromNode][toNode];
                });
        routing.addDimension(transitCallbackIndex, 120, 86400,
                false,
                "Time");

        RoutingDimension timeDimension = routing.getMutableDimension("Time");

        for (int i = determineStartFromVrpType(); i < durationMatrix.length; i++) {
            long index = manager.nodeToIndex(i);
            timeDimension.cumulVar(index).setRange(
                    this.timeWindows[i][0],
                    this.timeWindows[i][1]
            );
        }
    }

    private void addMaxOrderCountDimension(RoutingModel routing, RoutingIndexManager manager) {
        if (notEqualToDistanceMatrixLength(this.maxOrderDemands.length)) throw SegariRoutingErrors.invalidRoutingParameter("maxOrderDemands in addMaxOrderCountDimension");
        if (notEqualToVehicleNumber(this.maxOrderVehicleCapacities.length)) throw SegariRoutingErrors.invalidRoutingParameter("maxOrderVehicleCapacities in addMaxOrderCountDimension");
        final int maxOrderCountCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            return this.maxOrderDemands[fromNode];
        });
        routing.addDimensionWithVehicleCapacity(maxOrderCountCallbackIndex, 0,
                this.maxOrderVehicleCapacities,
                true,
                "MaxOrderCount");
    }

    private void addExtensionTurboInstanRatioDimension(RoutingModel routing, RoutingIndexManager manager) {
        if (notEqualToDistanceMatrixLength(this.extensionRatioDemands.length)) throw SegariRoutingErrors.invalidRoutingParameter("extensionRatioDemands in addExtensionTurboInstanRatioDimension");
        if (notEqualToVehicleNumber(this.extensionRatioVehicleCapacities.length)) throw SegariRoutingErrors.invalidRoutingParameter("extensionRatioVehicleCapacities in addExtensionTurboInstanRatioDimension");
        final int extensionTurboInstanRatioCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            return this.extensionRatioDemands[fromNode];
        });
        routing.addDimensionWithVehicleCapacity(extensionTurboInstanRatioCallbackIndex, 0,
                this.extensionRatioVehicleCapacities,
                true,
                "ExtensionTurboInstanRatio");
    }

    private void addMaxInstanOrderCountDimension(RoutingModel routing, RoutingIndexManager manager) {
        if (notEqualToDistanceMatrixLength(this.maxInstanDemands.length)) throw SegariRoutingErrors.invalidRoutingParameter("maxInstanDemands in addMaxInstanOrderCountDimension");
        if (notEqualToVehicleNumber(this.maxInstanVehicleCapacities.length)) throw SegariRoutingErrors.invalidRoutingParameter("maxInstanVehicleCapacities in addMaxInstanOrderCountDimension");
        final int maxInstanOrderCountCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            return this.maxInstanDemands[fromNode];
        });
        routing.addDimensionWithVehicleCapacity(maxInstanOrderCountCallbackIndex, 0,
                this.maxInstanVehicleCapacities,
                true,
                "MaxInstanOrderCount");
    }

    private void addMaxTurboOrderCountDimension(RoutingModel routing, RoutingIndexManager manager) {
        if (notEqualToDistanceMatrixLength(this.maxTurboDemands.length)) throw SegariRoutingErrors.invalidRoutingParameter("maxTurboDemands in addMaxTurboOrderCountDimension");
        if (notEqualToVehicleNumber(this.maxTurboVehicleCapacities.length)) throw SegariRoutingErrors.invalidRoutingParameter("maxTurboVehicleCapacities in addMaxTurboOrderCountDimension");
        final int maxTurboOrderCountCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            return this.maxTurboDemands[fromNode];
        });
        routing.addDimensionWithVehicleCapacity(maxTurboOrderCountCallbackIndex, 0,
                this.maxTurboVehicleCapacities,
                true,
                "MaxTurboOrderCount");
    }

    private void addLoadFactorDimension(RoutingModel routing, RoutingIndexManager manager) {
        if (notEqualToDistanceMatrixLength(this.loadFactorDemands.length)) throw SegariRoutingErrors.invalidRoutingParameter("loadFactorDemands in addLoadFactorDimension");
        if (notEqualToVehicleNumber(this.loadFactorVehicleCapacities.length)) throw SegariRoutingErrors.invalidRoutingParameter("loadFactorVehicleCapacities in addLoadFactorDimension");
        final int loadFactorCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            return this.loadFactorDemands[fromNode];
        });
        routing.addDimensionWithVehicleCapacity(loadFactorCallbackIndex, 0,
                this.loadFactorVehicleCapacities,
                true,
                "LoadFactor");
    }

    private boolean notEqualToDistanceMatrixLength(int length) {
        return length != this.distanceMatrix.length;
    }

    private boolean notEqualToVehicleNumber(int length) {
        return length != this.vehicleNumbers;
    }

    private void addPenaltyAndDropVisit(RoutingModel routing, RoutingIndexManager manager) {
        long penalty = 100_000;
        long mandatoryPenalty = 1_000_000_000;
        for (int i = determineStartFromVrpType(); i < this.distanceMatrix.length; ++i) {
            SegariRouteOrderDTO order = this.orders.get(i);
            routing.addDisjunction(new long[] {manager.nodeToIndex(i)}, mandatoryOrderIds.contains(order.id()) ? mandatoryPenalty : penalty);
        }
    }

    private static Assignment findSolution(RoutingModel routing) {
        RoutingSearchParameters searchParameters =
                main.defaultRoutingSearchParameters()
                        .toBuilder()
                        .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                        .setTimeLimit(Duration.newBuilder().setSeconds(60).build())
                        .build();
        return routing.solveWithParameters(searchParameters);
    }

    private List<ArrayList<Long>> getResult(RoutingModel routing, RoutingIndexManager manager,
                                                                  Assignment solution) {
        if (Objects.isNull(solution)) return Collections.emptyList();
        List<ArrayList<Long>> results = new ArrayList<>();
        for (int i = 0; i < this.vehicleNumbers; i++) {
            long index = routing.start(i);

            ArrayList<Long> route = new ArrayList<>();
            boolean hasExtension = false;
            while (!routing.isEnd(index)){
                final long thisRoute = manager.indexToNode(index);
                final SegariRouteOrderDTO order = this.orders.get((int) thisRoute);
                final long orderId = order.id();
                if (!hasExtension) hasExtension = Boolean.TRUE.equals(order.isExtension());
                if (orderId != -1L && orderId != -2L) route.add(orderId);
                index = solution.value(routing.nextVar(index));
            }

            if (this.hasSetResultMinimum){
                if (route.size() < this.minimumResult) continue;
            }
            if (this.hasResultMustContainExtension){
                if (!hasExtension) continue;
            }

            putResult(route, results);
        }

        // Verify all mandatory orders were visited
        if (!mandatoryOrderIds.isEmpty() && !results.isEmpty()) {
           if (!new HashSet<>(results.getFirst()).containsAll(mandatoryOrderIds)) return Collections.emptyList();
        }

        return results;
    }

    private void putResult(ArrayList<Long> route, List<ArrayList<Long>> results) {
        if (route.size() > 1 || SegariRouteType.TSP_SP_START_ARBITRARY_FINISH.equals(type)) results.add(route);
    }

    private void fillRequiredAttributes() {
        int startIndex = determineStartFromVrpType();
        int length = this.orders.size();

        final OSRMTableResponseDTO rawDistanceAndDurationMatrix = getRawDistanceAndDurationMatrix();

        this.distanceMatrix = getDistanceMatrix(length, rawDistanceAndDurationMatrix);
        this.maxOrderVehicleCapacities = initiateVehicleArray(this.vehicleNumbers, this.maxOrderCount);
        this.maxOrderDemands = initiateDemandArray(this.orders.size(), startIndex);
        this.start = getStart();
        this.finish = getFinish();

        long[] maxInstanDemands = new long[length];
        long[] maxTurboDemands = new long[length];
        long[] loadFactorDemands = new long[length];
        long[] extensionRatioDemands = new long[length];
        for (int i = startIndex; i < length; i++){
            if (this.hasMaxInstanOrderCountDimension) maxInstanDemands[i] = this.orders.get(i).isInstan() ? 1 : 0;
            if (this.hasMaxTurboOrderCountDimension) maxTurboDemands[i] = this.orders.get(i).isTurbo() ? 1 : 0;
            if (this.hasLoadFactorDimension) loadFactorDemands[i] = 0; // TODO - define load factor when possible
            if (this.hasExtensionTurboInstanRatioDimension) extensionRatioDemands[i] = this.orders.get(i).isExtension() ? this.extensionRatio : this.turboInstanRatio;
        }
        if(this.hasTimeWindowDimension && this.useOsrm){
            this.durationMatrix = getDurationMatrix(rawDistanceAndDurationMatrix, length);
        }
        if (this.hasMaxInstanOrderCountDimension) {
            this.maxInstanDemands = maxInstanDemands;
            this.maxInstanVehicleCapacities = initiateVehicleArray(this.vehicleNumbers, maxInstanOrderCount);
        }
        if (this.hasMaxTurboOrderCountDimension) {
            this.maxTurboDemands = maxTurboDemands;
            this.maxTurboVehicleCapacities = initiateVehicleArray(this.vehicleNumbers, maxTurboOrderCount);
        }
        if (this.hasLoadFactorDimension) {
            this.loadFactorDemands = loadFactorDemands;
            this.loadFactorVehicleCapacities = initiateVehicleArray(this.vehicleNumbers, maxLoadFactor);
        }
        if (this.hasExtensionTurboInstanRatioDimension) {
            this.extensionRatioDemands = extensionRatioDemands;
            this.extensionRatioVehicleCapacities = initiateVehicleArray(this.vehicleNumbers, determineRatioCapacity());
        }
    }

    private long[][] getDurationMatrix(OSRMTableResponseDTO rawDistanceAndDurationMatrix, int length) {
        long[][] durationMatrix = rawDistanceAndDurationMatrix.durations();
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (isDummyNode(i, j)){
                    durationMatrix[i][j] = 0;
                }
                if (isISpNode(i) && durationMatrix[i][j] > this.timeWindows[j][1]) {
                    this.timeWindows[j][1] = 43200;
                }
            }
        }
        return durationMatrix;
    }

    private OSRMTableResponseDTO getRawDistanceAndDurationMatrix() {
        if (this.useOsrm){
            final List<LatLong> latLongs = this.orders.stream()
                    .map(order -> new LatLong(order.latitude(), order.longitude()))
                    .toList();
            return osrmRestService.getDistanceMatrix(latLongs);
        }
        return new OSRMTableResponseDTO(new long[0][0], new long[0][0]);
    }

    private int determineRatioCapacity() {
        final int ex = (this.extensionCount >= this.maxOrderCount)
                ? Math.ceilDiv(this.extensionCount, this.vehicleNumbers)
                : this.extensionCount;
        int capacity = 0;
        for (int i = 0; i < this.maxOrderCount; i++) {
            if (i < ex){
                capacity += this.extensionRatio;
            }else {
                capacity += this.turboInstanRatio;
            }
        }
        return capacity;
    }

    private long[][] getDistanceMatrix(int length, OSRMTableResponseDTO rawDistanceAndDurationMatrix) {
        if (this.useOsrm){
            if (rawDistanceAndDurationMatrix.distances().length == length) {
                long[][] distanceMatrix = rawDistanceAndDurationMatrix.distances();
                for (int i = 0; i < length; i++) {
                    for (int j = 0; j < length; j++) {
                        if (isDummyNode(i, j)){
                            distanceMatrix[i][j] = 0;
                            continue;
                        }
                        long basicValue = distanceMatrix[i][j];
                        if (this.hasDistanceBetweenNodeDimension){
                            distanceMatrix[i][j] = basicValue > this.maxDistanceBetweenNodeInMeter ? this.maxTotalDistanceInMeter + 1 : basicValue;
                            continue;
                        }
                        if (this.hasDistanceWithSpDimension && isSpNode(i, j)){
                            distanceMatrix[i][j] = basicValue > this.maxDistanceWithSpInMeter ? this.maxTotalDistanceInMeter + 1 : basicValue;
                            continue;
                        }
                        if (this.hasDistanceBetweenOrderDimension){
                            distanceMatrix[i][j] = basicValue > this.maxDistanceBetweenOrderInMeter ? this.maxTotalDistanceInMeter + 1 : basicValue;
                        }
                    }
                }
                return distanceMatrix;
            }
        }

        long[][] distanceMatrix = new long[length][length];
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (isDummyNode(i, j)){
                    distanceMatrix[i][j] = 0;
                    continue;
                }
                long basicValue = Math.round(GeoUtils.getHaversineDistanceInMeter(
                        this.orders.get(i).latitude(),
                        this.orders.get(i).longitude(),
                        this.orders.get(j).latitude(),
                        this.orders.get(j).longitude()));
                if (this.hasDistanceBetweenNodeDimension){
                    distanceMatrix[i][j] = basicValue > this.maxDistanceBetweenNodeInMeter ? this.maxTotalDistanceInMeter + 1 : basicValue;
                    continue;
                }
                if (this.hasDistanceWithSpDimension && isSpNode(i, j)){
                    distanceMatrix[i][j] = basicValue > this.maxDistanceWithSpInMeter ? this.maxTotalDistanceInMeter + 1 : basicValue;
                    continue;
                }
                if (this.hasDistanceBetweenOrderDimension){
                    distanceMatrix[i][j] = basicValue > this.maxDistanceBetweenOrderInMeter ? this.maxTotalDistanceInMeter + 1 : basicValue;
                }
            }
        }
        return distanceMatrix;
    }

    private boolean isSpNode(int i, int j) {
        return SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(this.orders.get(i).type()) || SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(this.orders.get(j).type());
    }

    private boolean isISpNode(int i) {
        return SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(this.orders.get(i).type());
    }

    private boolean isJSpNode(int j) {
        return SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(this.orders.get(j).type());
    }

    private boolean isDummyNode(int i, int j) {
        return SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(this.orders.get(i).type()) || SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(this.orders.get(j).type());
    }

    private int[] getFinish() {
        if (SegariRouteType.VRP_ARBITRARY_START_AND_FINISH.equals(this.type)) return arrayOfZero(this.vehicleNumbers);
        if (SegariRouteType.VRP_SP_START_ARBITRARY_FINISH.equals(this.type)) return arrayOfZero(this.vehicleNumbers);
        if (SegariRouteType.TSP_FIX_START_ARBITRARY_FINISH.equals(this.type)) return arrayOfZero(this.vehicleNumbers);
        if (SegariRouteType.TSP_SP_START_ARBITRARY_FINISH.equals(this.type)) return arrayOfZero(this.vehicleNumbers);
        throw SegariRoutingErrors.typeNotSupported();
    }

    private int[] getStart() {
        if (SegariRouteType.VRP_ARBITRARY_START_AND_FINISH.equals(this.type)) return arrayOfZero(this.vehicleNumbers);
        if (SegariRouteType.VRP_SP_START_ARBITRARY_FINISH.equals(this.type)) return arrayOfOne(this.vehicleNumbers);
        if (SegariRouteType.TSP_FIX_START_ARBITRARY_FINISH.equals(this.type)) return new int[]{tspFixStartIndex};
        if (SegariRouteType.TSP_SP_START_ARBITRARY_FINISH.equals(this.type)) return arrayOfOne(this.vehicleNumbers);
        throw SegariRoutingErrors.typeNotSupported();
    }

    private static long[] initiateVehicleArray(long vehicleNumbers, int value) {
        long[] array = new long[(int) vehicleNumbers];
        Arrays.fill(array, value);
        return array;
    }

    private static long[] initiateDemandArray(int length, int start) {
        long[] array = new long[length];
        for (int i = start; i < array.length; i++) {
            array[i] = 1;
        }
        return array;
    }

    private static int[] arrayOfZero(int length) {
        int[] array = new int[length];
        Arrays.fill(array, 0);
        return array;
    }

    private static int[] arrayOfOne(int length) {
        int[] array = new int[length];
        Arrays.fill(array, 1);
        return array;
    }

    private int determineStartFromVrpType() {
        if (SegariRouteType.VRP_ARBITRARY_START_AND_FINISH.equals(this.type)) return 1;
        if (SegariRouteType.VRP_SP_START_ARBITRARY_FINISH.equals(this.type)) return 2;
        if (SegariRouteType.TSP_FIX_START_ARBITRARY_FINISH.equals(this.type)) {
            if (SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(orders.get(1).type())){
                return 2;
            }else {
                return 1;
            }
        }
        if (SegariRouteType.TSP_SP_START_ARBITRARY_FINISH.equals(this.type)) return 2;
        throw SegariRoutingErrors.typeNotSupported();
    }

    private static void injectVrpAttributes(SegariRoute segariRoute,
                                            int maxTotalDistanceInMeter, int maxOrderCount,
                                            int orderCount) {
        if (maxTotalDistanceInMeter <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxTotalDistanceInMeter in injectVrpAttributes");
        if (maxOrderCount <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxOrderCount in injectVrpAttributes");
        segariRoute.setMaxTotalDistanceInMeter(maxTotalDistanceInMeter);
        segariRoute.setMaxOrderCount(maxOrderCount);
        segariRoute.setVehicleNumbers((orderCount < maxOrderCount) ? 1 : Math.ceilDiv(orderCount, maxOrderCount));
    }

    private static void injectTspAttributes(SegariRoute segariRoute,
                                            int maxTotalDistanceInMeter, int maxOrderCount, int startIndex) {
        if (maxTotalDistanceInMeter <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxTotalDistanceInMeter in injectTspAttributes");
        if (maxOrderCount <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxOrderCount in injectTspAttributes");
        if (startIndex < 0) throw SegariRoutingErrors.invalidRoutingParameter("startIndex in injectTspAttributes");
        segariRoute.setMaxTotalDistanceInMeter(maxTotalDistanceInMeter);
        segariRoute.setMaxOrderCount(maxOrderCount);
        segariRoute.setVehicleNumbers(1);
        segariRoute.setTspFixStartIndex(startIndex);
    }

    enum SegariRouteType {
        VRP_SP_START_ARBITRARY_FINISH, VRP_ARBITRARY_START_AND_FINISH, TSP_FIX_START_ARBITRARY_FINISH, TSP_SP_START_ARBITRARY_FINISH
    }

    static {
        Loader.loadNativeLibraries();
    }
}
