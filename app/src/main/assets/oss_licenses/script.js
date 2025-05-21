fixes = {
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
            var link = document.createElement('a');
            link.className = 'icon';
            link.href = dependency.url;
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
