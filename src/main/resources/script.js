function getChartData(map, startDate, endDate) {
    const labels = Array.from(map.keys())
        .filter(x => startDate <= x && x <= endDate)
        .sort();

    return {
        labels: labels,
        datasets: [{
            label: 'NVDA-HUF',
            backgroundColor: 'rgb(0, 0, 255)',
            borderColor: 'rgb(0, 0, 255)',
            data: labels.map(key => map.get(key).nvdaHuf),
            yAxisID: 'y'
        }, {
            label: 'USD-HUF',
            hidden: true,
            backgroundColor: 'rgb(0, 255, 0)',
            borderColor: 'rgb(0, 255, 0)',
            data: labels.map(key => map.get(key).usdHuf),
            yAxisID: 'y1'
        }, {
            label: 'NVDA-USD',
            hidden: true,
            backgroundColor: 'rgb(255, 0, 0)',
            borderColor: 'rgb(255, 0, 0)',
            data: labels.map(key => map.get(key).nvdaUsd),
            yAxisID: 'y2'
        }]
    };
}

$(document).ready(function () {

    startDateInput = document.getElementById("start");
    endDateInput = document.getElementById("end");

    $.get("json", function (r) {
        const result = Object.entries(JSON.parse(r));
        map = new Map();

        var minDate = "9999-99-99";
        var maxDate = "0000-00-00";
        for (const [key, value] of result) {
            if (key < minDate) {
                minDate = key;
            }
            if (key > maxDate) {
                maxDate = key;
            }
            map.set(key, value);
        }

        startDateInput.setAttribute("min", minDate)
        startDateInput.setAttribute("max", maxDate)
        endDateInput.setAttribute("min", minDate)
        endDateInput.setAttribute("max", maxDate)
        endDate = new Date(maxDate);
        startDate = new Date(endDate);
        startDate.setMonth(endDate.getMonth() - 1);
        if (startDate.getMonth() == endDate.getMonth()) {
            startDate.setDate(0);
        }
        endDateInput.value = endDate.toISOString().split('T')[0]
        startDateInput.value = startDate.toISOString().split('T')[0]

        const data = getChartData(map, startDateInput.value, endDateInput.value)

        const config = {
            type: 'line',
            data: data,
            options: {
                responsive: true,
                interaction: {
                    mode: 'index',
                    intersect: 'false'
                },
                stacked: false,
                plugins: {
                    title: {
                        display: true,
                        text: 'NVDA HUF'
                    }
                },
                scales: {
                    y: {
                        type: 'linear',
                        display: true,
                        position: 'left'
                    },
                    y1: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        grid: {
                            drawOnChartArea: false, // only want the grid lines for one axis to show up
                        }
                    },
                    y2: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        grid: {
                            drawOnChartArea: false, // only want the grid lines for one axis to show up
                        }
                    }
                }
            }
        };

        myChart = new Chart(
            document.getElementById('myChart'),
            config
        );

        const updateChartInterval = function () {
            const data = getChartData(map, startDateInput.value, endDateInput.value)
            myChart.data.labels = data.labels;
            for (let i = 0; i < data.datasets.length; ++i) {
                myChart.data.datasets[i].data = data.datasets[i].data;
            }
            myChart.update();
        }

        startDateInput.addEventListener("input", updateChartInterval)

        endDateInput.addEventListener("input", updateChartInterval)

    });

});

