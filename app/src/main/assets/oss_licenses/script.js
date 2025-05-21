fixes = {
    "circleprogress": {
        "url": "https://github.com/lzyzsd/CircleProgress",
        "licenses": [
            {
                "license": "https://github.com/lzyzsd/CircleProgress/blob/master/README.md",
                "license_url": "https://github.com/lzyzsd/CircleProgress/blob/master/README.md"
            }
        ]
    },
    "core": {
        "url": "https://github.com/afollestad/material-dialogs",
        "licenses": [
            {
                "license": "Apache License 2.0",
                "license_url": "https://github.com/afollestad/material-dialogs/blob/master/LICENSE.md"
            }
        ]
    },
    "jdk9-deps": { // No license, compile-time dependency
        "url": "https://github.com/pengrad/jdk9-deps"
    },
    "keyboardvisibilityevent": {
        "url": "https://github.com/yshrsmz/KeyboardVisibilityEvent",
        "licenses": [
            {
                "license": "Apache License 2.0",
                "license_url": "https://github.com/yshrsmz/KeyboardVisibilityEvent/blob/master/LICENSE"
            }
        ]
    },
    "MPAndroidChart": {
        "url": "https://github.com/PhilJay/MPAndroidChart",
        "licenses": [
            {
                "license": "Apache License 2.0",
                "license_url": "https://github.com/PhilJay/MPAndroidChart/blob/master/LICENSE"
            }
        ]
    },
    "RxActivityResult": {
        "url": "https://github.com/VictorAlbertos/RxActivityResult",
        "licenses": [
            {
                "license": "Apache License 2.0",
                "license_url": "https://github.com/VictorAlbertos/RxActivityResult/blob/2.x/LICENSE.txt"
            }
        ]
    },
    "shts/TriangleLabelView": {
        "licenses": [
            {
                "license": "Apache License 2.0",
                "license_url": "https://github.com/shts/TriangleLabelView#license"
            }
        ]
    },
    "tileview": {
        "url": "https://github.com/moagrius/TileView",
        "licenses": [
            {
                "license": "MIT License",
                "license_url": "https://github.com/moagrius/TileView/blob/master/LICENSE"
            }
        ]
    },
};

function applyFixes(dependencies) {
    for (var i in dependencies) {
        var dependency = dependencies[i];

        if (dependency.url === null) {
            if (dependency.project in fixes && "url" in fixes[dependency.project])
                dependency.url = fixes[dependency.project].url;
        }

        if (dependency.licenses.length == 0) {
            if (dependency.project in fixes && "licenses" in fixes[dependency.project])
                dependency.licenses = fixes[dependency.project].licenses;
        }
    }
}

function createLabelWithValue(labelText, valueText) {
    var row = document.createElement('div');
    row.className = 'row';
    var versionLabel = document.createElement('span')
    versionLabel.className = 'label';
    versionLabel.innerText = labelText
    var versionValue = document.createElement('span')
    versionValue.className = 'value';
    versionValue.innerText = valueText;
    row.appendChild(versionLabel);
    row.appendChild(versionValue);
    return row;
}

function processDependencies(dependencies) {
    for (var i in dependencies) {
        var dependency = dependencies[i];
        var elem = document.createElement('div');
        elem.className = 'container';
        var header = document.createElement('h1');
        header.innerText = dependency.project
        elem.appendChild(header);

        if (dependency.url !== null) {
            var icon = document.createElement('img');
            icon.src = 'ic-arrow-out-square.svg'
            var link = document.createElement('a');
            link.className = 'icon';
            link.href = dependency.url;
            link.appendChild(icon);
            header.appendChild(link);
        }

        if (dependency.version !== null) {
            elem.appendChild(createLabelWithValue('Version:', dependency.version));
        }

        if (dependency.developers.length > 0) {
            elem.appendChild(createLabelWithValue('By:', dependency.developers.join(', ')));
        }

        if (dependency.licenses.length > 0) {
            var licensesBlock = document.createElement('div');
            licensesBlock.className = 'licenses';
            var licensesLabel = document.createElement('div');
            licensesLabel.className = 'licensesLabel';
            licensesLabel.innerText = 'Licenses:'
            licensesBlock.appendChild(licensesLabel);
            for (j in dependency.licenses) {
                var license = dependency.licenses[j];
                var licenseElem = document.createElement('a');
                licenseElem.href = license.license_url;
                licenseElem.innerText = license.license;
                licensesBlock.appendChild(licenseElem);
            }
            elem.appendChild(licensesBlock);
        }

        document.body.appendChild(elem);
    }
}

applyFixes(dependencies);
processDependencies(dependencies);
processDependencies(extraDependencies);
