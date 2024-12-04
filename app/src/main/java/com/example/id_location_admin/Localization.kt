package com.example.id_location_admin

import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt

fun refinePositionByGaussNewton(
    distances: List<Float>,
    anchorPosition: List<Point>
): Point {
    // 초기 위치 설정
    var currentPosition = calcMiddleBy4Side(distances, anchorPosition)

    // 허용 오차와 최대 반복 횟수 설정
    val tolerance = 1e-6f
    val maxIterations = 100
    var iteration = 0

    while (iteration < maxIterations) {
        val numAnchors = distances.size
        val jacobian = Array(numAnchors) { FloatArray(3) } // 자코비안 행렬
        val residuals = FloatArray(numAnchors) // 잔차 벡터

        // 잔차와 자코비안 계산
        for (i in distances.indices) {
            val dx = currentPosition.x - anchorPosition[i].x
            val dy = currentPosition.y - anchorPosition[i].y
            val dz = currentPosition.z - 0 // z 값은 2D에서 0으로 설정
            val predictedDistance = sqrt(dx * dx + dy * dy + dz * dz)

            residuals[i] = distances[i] - predictedDistance
            val distanceInverse = if (predictedDistance != 0f) 1 / predictedDistance else 0f

            jacobian[i][0] = -dx * distanceInverse
            jacobian[i][1] = -dy * distanceInverse
            jacobian[i][2] = -dz * distanceInverse
        }

        // 자코비안 전치 계산
        val jacobianT = transitionMatrix(jacobian)

        // H = J^T * J 계산
        val hessian = multiplyMatrixMatrix(jacobianT, jacobian)

        // g = J^T * residuals 계산
        val gradient = multiplyMatrixVector(jacobianT, residuals)

        // H 역행렬 계산
        val hessianInverse = invertMatrix(hessian)

        // Δx = H^-1 * g 계산
        val delta = multiplyMatrixVector(hessianInverse, gradient)

        // 위치 업데이트
        currentPosition = Point(
            currentPosition.x + delta[0],
            currentPosition.y + delta[1],
            currentPosition.z + delta[2]
        )

        // 변화량 확인
        if (sqrt(delta[0].pow(2) + delta[1].pow(2) + delta[2].pow(2)) < tolerance) break

        iteration++
    }

    Log.d("refine_gauss_newton", "Final position: $currentPosition after $iteration iterations")
    return currentPosition
}

fun calcMiddleBy4Side(distances: List<Float>, anchorPosition: List<Point>): Point {
    Log.d("QWERQWEER",anchorPosition.toString())
    val x = anchorPosition.map{it.x}
    val y = anchorPosition.map{it.y}
    val d = distances.map{it}

    val A1 = arrayOf(
        floatArrayOf(2 * (x[1] - x[0]), 2 * (y[1] - y[0])),
        floatArrayOf(2 * (x[3] - x[2]), 2 * (y[3] - y[2]))
    )
    val B1 = floatArrayOf(
        generateRight(x[1],y[1],d[1]) - generateRight(x[0],y[0],d[0]),
        generateRight(x[3],y[3],d[3]) - generateRight(x[2],y[2],d[2])
    )
    val A2 = arrayOf(
        floatArrayOf(2 * (x[1] - x[2]), 2 * (y[1] - y[2])),
        floatArrayOf(2 * (x[3] - x[0]), 2 * (y[3] - y[0]))
    )
    val B2 = floatArrayOf(
        generateRight(x[1],y[1],d[1]) - generateRight(x[2],y[2],d[2]),
        generateRight(x[3],y[3],d[3]) - generateRight(x[0],y[0],d[0])
    )
    val A3 = arrayOf(
        floatArrayOf(2 * (x[0] - x[2]), 2 * (y[0] - y[2])),
        floatArrayOf(2 * (x[3] - x[1]), 2 * (y[3] - y[1]))
    )
    val B3 = floatArrayOf(
        generateRight(x[0],y[0],d[0]) - generateRight(x[2],y[2],d[2]),
        generateRight(x[3],y[3],d[3]) - generateRight(x[1],y[1],d[1])
    )
    var resultVectorList: List<FloatArray> = mutableListOf(multiplyMatrixVector(invertMatrix(A1),B1), multiplyMatrixVector(invertMatrix(A2),B2), multiplyMatrixVector(invertMatrix(A3),B3))
    var meanPoint: Point = Point()
    resultVectorList.forEach{
        if(isPointInSquare(Point(it[0],it[1]), calcBy4Side(distances,anchorPosition))){
            meanPoint = Point(it[0],it[1])
        }
    }

    val meanArray = FloatArray(4)
    val zArray:MutableList<Float> = mutableListOf()
    for(i in 0..3){
        meanArray[i] = sqrt((meanPoint.x-anchorPosition[i].x).pow(2)+(meanPoint.y-anchorPosition[i].y).pow(2))
    }
    for(i in 0..3){
        if(distances[i].pow(2) >= meanArray[i].pow(2))
            zArray.add(sqrt(distances[i].pow(2) - meanArray[i].pow(2)))
    }
    Log.d("calc_4", "${Point(meanPoint.x, meanPoint.y, zArray.average().toFloat())}")
    return Point(meanPoint.x,meanPoint.y,zArray.average().toFloat())
}

fun sign(o: Point, a: Point, b: Point): Float {
    return (o.x - b.x) * (a.y - b.y) - (a.x - b.x) * (o.y - b.y)
}

fun isPointInTriangle(p: Point, p0: Point, p1: Point, p2: Point): Boolean {
    val b1 = sign(p, p0, p1) < 0.0
    val b2 = sign(p, p1, p2) < 0.0
    val b3 = sign(p, p2, p0) < 0.0

    return (b1 == b2) && (b2 == b3)
}

fun isPointInSquare(p: Point, quad: List<Point>): Boolean {
    // 사각형을 두 개의 삼각형으로 분할
    val t1 = listOf(quad[0], quad[1], quad[2])
    val t2 = listOf(quad[0], quad[2], quad[3])

    return isPointInTriangle(p, t1[0], t1[1], t1[2]) || isPointInTriangle(p, t2[0], t2[1], t2[2])
}

fun calcBy4Side(distances: List<Float>, anchorPosition: List<Point>): List<Point>{
    var results = emptyList<Point>()
    for (i in distances.indices) {
        results = results.plus(
            calcBy3Side(
                listOf(
                    distances[(i + 1) % distances.size],
                    distances[(i + 2) % distances.size],
                    distances[(i + 3) % distances.size]
                ),
                listOf(
                    anchorPosition[(i + 1) % distances.size],
                    anchorPosition[(i + 2) % distances.size],
                    anchorPosition[(i + 3) % distances.size]
                )
            )
        )
    }
    return results
}
fun calcBy3Side(distances: List<Float>, anchorPosition: List<Point>): Point {

    if(distances.size < 3) return Point(-66.66f,-66.66f,-66.66f)

    val x = anchorPosition.map{it.x}
    val y = anchorPosition.map{it.y}
    val z = anchorPosition.map{it.z}
    val d = distances.map{it}

    val A = arrayOf(
        floatArrayOf(2 * (x[1] - x[0]), 2 * (y[1] - y[0])),
        floatArrayOf(2 * (x[2] - x[0]), 2 * (y[2] - y[0]))
    )
    val B = floatArrayOf(
        generateRight(x[1],y[1],d[1]) - generateRight(x[0],y[0],d[0]),
        generateRight(x[2],y[2],d[2]) - generateRight(x[0],y[0],d[0])
    )
    val Ainv = invertMatrix(A)
    val result = multiplyMatrixVector(Ainv, B)


    return Point(result[0], result[1])
}
/*
fun calcByDoubleAnchor2Distance(anchor1:Int, anchor2: Int,anchorPositions: List<Point>, distances: List<Float>, lastZ:Float = 0f): List<Point>{
    val d1 = distances[anchor1]
    val d2 = distances[anchor2]

    return calcByDoubleAnchor2Distance(d1,d2, anchorPositions[anchor1],anchorPositions[anchor2])
}
fun calcByDoubleAnchor2Distance(d1:Float, d2:Float, p1: Point, p2:Point): List<Point>{
    val A = 4 * (p1.y-p2.y).pow(2)
    val B = -2 * (p1.y-p2.y) * (p2.x.pow(2)-p1.x.pow(2)+p1.y.pow(2)-p2.y.pow(2)+d2.pow(2)-d1.pow(2)+ 2 * p1.x * p2.x)
    val C = (p2.x.pow(2)-p1.x.pow(2)+p1.y.pow(2)-p2.y.pow(2)+d2.pow(2)-d1.pow(2)+ 2 * p1.x * p2.x).pow(2) - 4 * (p1.x - p2.x).pow(2) * d1.pow(2)
    val y1 = (-B+sqrt(B.pow(2)-4*A*C))/(2*A)
    val y2 = (-B-sqrt(B.pow(2)-4*A*C))/(2*A)
    val x1 = (generateRight(p1.x,p1.y,d1)-generateRight(p2.x,p2.y,d2)- 2*y1*(p1.y-p2.y))/(2*(p1.x-p2.x))
    val x2 = (generateRight(p1.x,p1.y,d1)-generateRight(p2.x,p2.y,d2)- 2*y2*(p1.y-p2.y))/(2*(p1.x-p2.x))
    return listOf(Point(x1,y1), Point(x2,y2))
}*/

fun calcByDoubleAnchor(distances: List<Float>,anchorPositions: List<Point>,actionSquare: List<Point>): Point {
    return calcByDoubleAnchor(0,1,distances, anchorPositions,actionSquare)
}
fun calcByDoubleAnchor(anchor1:Int, anchor2: Int, distances: List<Float>, anchorPositions: List<Point>, actionSquare: List<Point>): Point {
    val p1 = anchorPositions[anchor1]; val p2 = anchorPositions[anchor2]
    val distanceByAnchor:Float = p1.getDistance(p2)
    val cosTheta: Float = (distances[anchor1].pow(2)+distanceByAnchor.pow(2)-distances[anchor2].pow(2))/(2*distances[anchor1]*distanceByAnchor)
    val tanTheta: Float = sqrt(1-cosTheta.pow(2)) /cosTheta
    val m = (p2.y-p1.y)/(p2.x-p1.x)
    val mPrime = (m+tanTheta)/(1-m*tanTheta)
    val A = arrayOf(
        floatArrayOf((p2.x-p1.x)*2,(p2.y-p1.y)*2),
        floatArrayOf(-mPrime, 1f)
    )
    val B = floatArrayOf(
        (generateRight(p2.x,p2.y,distances[anchor2])- generateRight(p1.x,p1.y,distances[anchor1])),
        (p1.y- mPrime * p1.x)
    )
    val X = multiplyMatrixVector(invertMatrix(A),B)

    if((actionSquare.size>=4 && isPointInSquare(Point(X[0],X[1]),actionSquare)) || actionSquare.size>=3 && isPointInTriangle(Point(X[0],X[1]),actionSquare[0],actionSquare[1],actionSquare[2]) ){
        return Point(X[0], X[1])
    }else{
        //val cosTheta2: Float = (distances[anchor2].pow(2)+distanceByAnchor.pow(2)-distances[anchor1].pow(2))/(2*distances[anchor2]*distanceByAnchor)
        //val tanTheta2: Float = sqrt(1-cosTheta2.pow(2))/cosTheta2
        val mPrime2 = (m-tanTheta)/(1+m*tanTheta)
        val A2 = arrayOf(
            floatArrayOf((p2.x-p1.x)*2,(p2.y-p1.y)*2),
            floatArrayOf(-mPrime2, 1f)
        )
        val B2 = floatArrayOf(
            (generateRight(p2.x,p2.y,distances[anchor2])- generateRight(p1.x,p1.y,distances[anchor1])),
            (p1.y- mPrime2 * p1.x)
        )
        val X2 = multiplyMatrixVector(invertMatrix(A2),B2)
        return Point(X2[0], X2[1])
    }
}

fun generateRight(x:Float, y:Float, d:Float, z: Float= 0f): Float{
    return x*x + y*y + z*z - d*d
}